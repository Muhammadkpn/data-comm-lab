package com.learn.datacomm.gateway.filter;

import com.learn.datacomm.gateway.GatewayErrors;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Autentikasi DI EDGE: request tanpa kredensial valid ditolak gateway (401) dan tidak
 * pernah menyentuh service di belakangnya. Service upstream cukup percaya header
 * `X-Client-Id` yang ditambahkan gateway (dengan syarat upstream TIDAK bisa diakses
 * langsung dari luar -- network policy / mTLS antara gateway dan service).
 *
 * Demo ini memakai API key; gateway sungguhan juga bisa memverifikasi JWT (lihat auth-demo).
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String CLIENT_ATTRIBUTE = "gateway.clientId";

    private final Map<String, String> keyToClient;

    public ApiKeyAuthFilter(Map<String, String> keyToClient) {
        this.keyToClient = keyToClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String key = req.getHeader("X-API-Key");
        String client = key == null ? null : keyToClient.get(key);
        if (client == null) {
            GatewayErrors.write(req, res, 401, "unauthenticated", "Header X-API-Key tidak ada atau tidak dikenal");
            return;
        }
        req.setAttribute(CLIENT_ATTRIBUTE, client);
        chain.doFilter(req, res);
    }
}
