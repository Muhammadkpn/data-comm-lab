package com.learn.datacomm.rest.filter;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Cara MENGOMUNIKASIKAN deprecation lewat protokol, bukan cuma lewat email/changelog.
 * Endpoint lama `/transfers` (v1) tetap berfungsi persis seperti sebelumnya, tapi setiap
 * response-nya membawa:
 *
 *   Deprecation: true                         endpoint ini sudah tidak disarankan
 *   Sunset: Wed, 31 Dec 2026 23:59:59 GMT     tanggal endpoint akan dimatikan (RFC 8594)
 *   Link: </v2/transfers>; rel="successor-version"   ke mana client harus pindah
 *
 * Client yang baik (atau API gateway / monitoring) bisa mendeteksi header ini dan
 * memberi peringatan jauh sebelum endpoint benar-benar dimatikan.
 */
@Component
public class DeprecationHeaderFilter extends OncePerRequestFilter {

    static final String SUNSET = "Wed, 31 Dec 2026 23:59:59 GMT";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/transfers") || path.startsWith("/transfers/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Deprecation", "true");
        response.setHeader("Sunset", SUNSET);
        response.setHeader("Link", "</v2/transfers>; rel=\"successor-version\"");
        chain.doFilter(request, response);
    }
}
