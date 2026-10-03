package com.learn.datacomm.cors;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Log setiap request API beserta Origin-nya. Poin penting yang terlihat di log ini:
 * request lintas origin TETAP SAMPAI ke server dan TETAP diproses walau browser
 * kemudian memblokir JavaScript membaca response-nya. CORS melindungi DATA RESPONSE
 * di browser user, bukan melindungi server dari request. (Itu tugas auth & CSRF token.)
 */
@Component
@Order(0)
public class RequestLogFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(req, res);
        String preflight = "OPTIONS".equals(req.getMethod()) && req.getHeader("Access-Control-Request-Method") != null
                ? " (PREFLIGHT untuk " + req.getHeader("Access-Control-Request-Method") + ")" : "";
        System.out.printf("[SERVER] %s %s Origin=%s -> %d, Allow-Origin=%s%s%n",
                req.getMethod(), req.getRequestURI(), req.getHeader("Origin"), res.getStatus(),
                res.getHeader("Access-Control-Allow-Origin"), preflight);
    }
}
