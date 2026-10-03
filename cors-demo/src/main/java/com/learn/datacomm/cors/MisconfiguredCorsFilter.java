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
 * MISKONFIGURASI YANG UMUM DI LAPANGAN -- untuk /api/misconfig/** saja.
 *
 * "Supaya CORS error-nya hilang", developer memantulkan header Origin apa pun ke
 * Access-Control-Allow-Origin DAN mengaktifkan Allow-Credentials. Hasilnya: situs
 * PENYERANG mana pun (https://evil.example) bisa memanggil API dengan cookie korban
 * dan MEMBACA response-nya (saldo, data pribadi). Ini lebih buruk daripada tanpa CORS.
 *
 * Spring sengaja tidak mengizinkan allowedOrigins("*") + allowCredentials(true); filter
 * manual ini mem-bypass pengaman tersebut, persis seperti yang sering terjadi di kode nyata.
 */
@Component
@Order(1)
public class MisconfiguredCorsFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/misconfig/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String origin = req.getHeader("Origin");
        if (origin != null) {
            res.setHeader("Access-Control-Allow-Origin", origin); // <-- BUG: percaya siapa saja
            res.setHeader("Access-Control-Allow-Credentials", "true");
        }
        chain.doFilter(req, res);
    }
}
