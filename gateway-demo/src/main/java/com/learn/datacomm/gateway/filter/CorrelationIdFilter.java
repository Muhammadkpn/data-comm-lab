package com.learn.datacomm.gateway.filter;

import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Memberi setiap request satu `X-Request-Id`, atau memakai yang dikirim client. Id ini
 * diteruskan ke upstream dan dikembalikan ke client, sehingga satu transaksi bisa dilacak
 * di log gateway, log service, dan APM dengan satu kata kunci.
 * (Di production biasanya standar W3C `traceparent` dari OpenTelemetry / Elastic APM.)
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = "gateway.requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String id = req.getHeader(HEADER);
        if (id == null || id.isEmpty() || id.length() > 64) {
            id = UUID.randomUUID().toString().substring(0, 12);
        }
        req.setAttribute(ATTRIBUTE, id);
        res.setHeader(HEADER, id);
        chain.doFilter(req, res);
    }
}
