package com.learn.datacomm.auth.config;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * API key: string rahasia statis per PARTNER (bukan per user). Cocok untuk integrasi
 * server-to-server sederhana (mis. mitra e-wallet memanggil API kurs).
 *
 * Kelemahan dibanding OAuth client credentials: tidak kedaluwarsa sendiri, tidak punya
 * scope granular kecuali dibuat manual, dan rotasi harus dikoordinasikan dengan partner.
 * Selalu kirim lewat HEADER (bukan query string) supaya tidak tercatat di access log/URL history.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    private final Map<String, String> keyToPartner;

    public ApiKeyFilter(Map<String, String> keyToPartner) {
        this.keyToPartner = keyToPartner;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader("X-API-Key");
        String partner = key == null ? null : keyToPartner.get(key);
        if (partner != null) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    partner, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_PARTNER"))));
        }
        chain.doFilter(request, response);
    }
}
