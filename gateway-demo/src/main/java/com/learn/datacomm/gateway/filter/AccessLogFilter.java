package com.learn.datacomm.gateway.filter;

import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Satu baris log per request, di SATU tempat untuk semua service di belakang gateway:
 * request id, client, method, path, status, latency. Dari log seperti ini dashboard
 * error rate & p99 latency per route dibangun -- alasan status code yang benar itu penting.
 */
public class AccessLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            System.out.printf("[GATEWAY] id=%s client=%s %s %s -> %d (%d ms)%n",
                    req.getAttribute(CorrelationIdFilter.ATTRIBUTE),
                    req.getAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE),
                    req.getMethod(), req.getRequestURI(), res.getStatus(), ms);
        }
    }
}
