package com.learn.datacomm.gateway.proxy;

import com.learn.datacomm.gateway.GatewayConfig;
import com.learn.datacomm.gateway.GatewayErrors;
import com.learn.datacomm.gateway.filter.ApiKeyAuthFilter;
import com.learn.datacomm.gateway.filter.CorrelationIdFilter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reverse proxy: cocokkan prefix path ke route, teruskan request ke upstream, salin
 * response kembali ke client.
 *
 * Status code yang DIBUAT gateway sendiri (bukan diteruskan dari upstream):
 *   404  tidak ada route yang cocok
 *   502  Bad Gateway      -- upstream tidak bisa dihubungi / koneksi ditolak
 *   504  Gateway Timeout  -- upstream terhubung tapi tidak menjawab dalam read-timeout
 * Bedanya penting saat on-call: 502 = service mati/tidak ada; 504 = service hidup tapi lambat.
 */
@RestController
public class ProxyController {

    /**
     * Hop-by-hop header berlaku untuk SATU koneksi saja, tidak boleh diteruskan (RFC 7230).
     * Host & Content-Length dihitung ulang oleh HTTP client; X-API-Key dibuang supaya
     * kredensial client tidak bocor ke service internal.
     */
    private static final Set<String> NOT_FORWARDED = new HashSet<>(Arrays.asList(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailer",
            "transfer-encoding", "upgrade", "host", "content-length", "x-api-key"));

    private final GatewayConfig config;
    private final RestTemplate http;

    public ProxyController(GatewayConfig config) {
        this.config = config;
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(config.getConnectTimeoutMs());
        factory.setReadTimeout(config.getReadTimeoutMs());
        this.http = new RestTemplate(factory);
        // Gateway meneruskan 4xx/5xx upstream apa adanya, jangan dijadikan exception.
        this.http.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
            }
        });
    }

    @RequestMapping("/api/**")
    public void proxy(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String path = req.getRequestURI();
        Map.Entry<String, String> route = findRoute(path);
        if (route == null) {
            GatewayErrors.write(req, res, 404, "no-route", "Tidak ada route untuk " + path);
            return;
        }

        String target = route.getValue() + path.substring(route.getKey().length())
                + (req.getQueryString() == null ? "" : "?" + req.getQueryString());

        HttpHeaders headers = new HttpHeaders();
        for (Enumeration<String> names = req.getHeaderNames(); names.hasMoreElements(); ) {
            String name = names.nextElement();
            if (!NOT_FORWARDED.contains(name.toLowerCase())) {
                headers.put(name, Collections.list(req.getHeaders(name)));
            }
        }
        // Informasi yang hanya diketahui gateway, diteruskan ke upstream:
        headers.set(CorrelationIdFilter.HEADER, String.valueOf(req.getAttribute(CorrelationIdFilter.ATTRIBUTE)));
        headers.set("X-Client-Id", String.valueOf(req.getAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE)));
        headers.set("X-Forwarded-For", req.getRemoteAddr());
        headers.set("X-Forwarded-Host", req.getHeader("Host"));
        headers.set("X-Forwarded-Proto", req.getScheme());

        byte[] body = StreamUtils.copyToByteArray(req.getInputStream());
        ResponseEntity<byte[]> upstream;
        try {
            upstream = http.exchange(URI.create(target), HttpMethod.resolve(req.getMethod()),
                    new HttpEntity<>(body.length == 0 ? null : body, headers), byte[].class);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                GatewayErrors.write(req, res, 504, "upstream-timeout",
                        "Upstream tidak menjawab dalam " + config.getReadTimeoutMs() + " ms");
            } else {
                GatewayErrors.write(req, res, 502, "upstream-unavailable",
                        "Upstream tidak bisa dihubungi: " + e.getCause().getClass().getSimpleName());
            }
            return;
        }

        res.setStatus(upstream.getStatusCodeValue());
        upstream.getHeaders().forEach((name, values) -> {
            if (!NOT_FORWARDED.contains(name.toLowerCase())) {
                values.forEach(v -> res.addHeader(name,
                        HttpHeaders.LOCATION.equalsIgnoreCase(name) ? rewriteLocation(v, route) : v));
            }
        });
        if (upstream.getBody() != null) {
            res.getOutputStream().write(upstream.getBody());
        }
    }

    /**
     * Upstream menjawab `Location: /v2/transfers/TRF-0001` -- path INTERNAL yang tidak bisa
     * diakses client. Gateway menerjemahkannya kembali ke path publik `/api/transfers/TRF-0001`.
     * Tanpa ini, struktur internal bocor dan link di response rusak.
     */
    private static String rewriteLocation(String location, Map.Entry<String, String> route) {
        String upstreamPath = URI.create(route.getValue()).getPath();
        if (location.startsWith(route.getValue())) {
            return route.getKey() + location.substring(route.getValue().length());
        }
        if (location.startsWith(upstreamPath)) {
            return route.getKey() + location.substring(upstreamPath.length());
        }
        return location;
    }

    /** Prefix terpanjang yang cocok menang (mis. /api/accounts vs /api/accounts-v3). */
    private Map.Entry<String, String> findRoute(String path) {
        Map.Entry<String, String> best = null;
        for (Map.Entry<String, String> r : config.getRoutes().entrySet()) {
            boolean matches = path.equals(r.getKey()) || path.startsWith(r.getKey() + "/");
            if (matches && (best == null || r.getKey().length() > best.getKey().length())) {
                best = r;
            }
        }
        return best;
    }
}
