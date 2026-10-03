package com.learn.datacomm.cors;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Kebijakan CORS yang BENAR untuk /api/cors/**:
 *
 *   - allowedOrigins EKSPLISIT (bukan "*"), karena endpoint ini juga menerima cookie
 *     (allowCredentials). Spring bahkan menolak kombinasi "*" + credentials saat startup.
 *   - allowedMethods & allowedHeaders seperlunya saja.
 *   - exposedHeaders: header response yang BOLEH dibaca JavaScript. Tanpa ini, script hanya
 *     bisa membaca segelintir header "aman" (Content-Type, dll), bukan X-Request-Id/Location.
 *   - maxAge: browser boleh menyimpan hasil preflight selama 10 menit, jadi tidak perlu
 *     OPTIONS sebelum setiap request.
 *
 * /api/no-cors/** sengaja TIDAK didaftarkan di sini.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    public static final String FRONTEND_ORIGIN = "http://localhost:8086";

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/cors/**")
                .allowedOrigins(FRONTEND_ORIGIN)
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "Idempotency-Key")
                .exposedHeaders("X-Request-Id", "Location")
                .allowCredentials(true)
                .maxAge(600);
    }
}
