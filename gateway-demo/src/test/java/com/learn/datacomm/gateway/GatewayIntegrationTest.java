package com.learn.datacomm.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gateway sungguhan (port acak) di depan upstream palsu dari JDK HttpServer, supaya
 * yang diuji benar-benar perilaku proxy lewat jaringan.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    private static final HttpServer UPSTREAM = startUpstream();

    @Autowired
    private TestRestTemplate http;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + UPSTREAM.getAddress().getPort();
        registry.add("gateway.routes[/api/svc]", () -> base + "/internal/svc");
        registry.add("gateway.routes[/api/slow]", () -> base + "/slow");
        registry.add("gateway.routes[/api/down]", () -> "http://localhost:1/x");
        registry.add("gateway.read-timeout-ms", () -> "500");
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/internal/svc", ex -> {
                // Pantulkan header yang diterima upstream sebagai body.
                StringBuilder sb = new StringBuilder();
                ex.getRequestHeaders().forEach((k, v) -> sb.append(k.toLowerCase()).append('=').append(v.get(0)).append('\n'));
                sb.append("path=").append(ex.getRequestURI()).append('\n');
                ex.getResponseHeaders().add("Location", "/internal/svc/NEW-1");
                write(ex, 201, sb.toString());
            });
            server.createContext("/slow", ex -> {
                try {
                    Thread.sleep(1_500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                write(ex, 200, "late");
            });
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool()); // /slow tidak boleh memblokir request lain
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    @AfterAll
    static void stop() {
        UPSTREAM.stop(0);
    }

    private ResponseEntity<String> call(String path, String apiKey, HttpHeaders extra) {
        HttpHeaders h = extra == null ? new HttpHeaders() : extra;
        if (apiKey != null) {
            h.set("X-API-Key", apiKey);
        }
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    @Test
    void tanpaApiKeyDitolakDiEdge() {
        ResponseEntity<String> res = call("/api/svc/a", null, null);
        assertEquals(401, res.getStatusCodeValue());
        assertTrue(res.getHeaders().getContentType().toString().startsWith("application/problem+json"));
    }

    @Test
    void proxyMeneruskanHeaderYangTepatDanMenulisUlangLocation() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Request-Id", "trace-42");
        ResponseEntity<String> res = call("/api/svc/a/b?q=1", "key-partner", h);

        assertEquals(201, res.getStatusCodeValue());
        String upstreamSaw = res.getBody();
        assertTrue(upstreamSaw.contains("path=/internal/svc/a/b?q=1"));
        assertTrue(upstreamSaw.contains("x-client-id=partner-x"));
        assertTrue(upstreamSaw.contains("x-request-id=trace-42"));
        assertTrue(upstreamSaw.contains("x-forwarded-for="));
        assertFalse(upstreamSaw.contains("x-api-key"), "kredensial client tidak boleh diteruskan ke upstream");

        assertEquals("trace-42", res.getHeaders().getFirst("X-Request-Id"));
        assertEquals("/api/svc/NEW-1", res.getHeaders().getFirst("Location"));
        assertNotNull(res.getHeaders().getFirst("X-RateLimit-Remaining"));
    }

    @Test
    void upstreamLambat504UpstreamMati502RouteTidakAda404() {
        assertEquals(504, call("/api/slow", "key-partner", null).getStatusCodeValue());
        assertEquals(502, call("/api/down", "key-partner", null).getStatusCodeValue());
        assertEquals(404, call("/api/nothing-here", "key-partner", null).getStatusCodeValue());
    }

    @Test
    void burstMelewatiKuota429DenganRetryAfter() {
        ResponseEntity<String> last = null;
        for (int i = 0; i < 7; i++) {
            last = call("/api/svc/x", "key-mobile", null);
        }
        assertEquals(429, last.getStatusCodeValue());
        assertEquals("1", last.getHeaders().getFirst("Retry-After"));
        assertEquals("0", last.getHeaders().getFirst("X-RateLimit-Remaining"));
    }
}
