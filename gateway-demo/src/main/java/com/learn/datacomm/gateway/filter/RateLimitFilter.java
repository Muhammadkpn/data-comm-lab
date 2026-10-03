package com.learn.datacomm.gateway.filter;

import com.learn.datacomm.gateway.GatewayErrors;
import com.learn.datacomm.gateway.ratelimit.RateLimiter;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Rate limit PER CLIENT (dari API key, bukan per IP -- banyak user mobile bisa berbagi
 * satu IP publik operator karena NAT).
 *
 * Kuota dikomunikasikan di SETIAP response, supaya client yang baik bisa mengerem
 * sendiri sebelum ditolak:
 *   X-RateLimit-Limit      kuota
 *   X-RateLimit-Remaining  sisa
 *   X-RateLimit-Reset      detik sampai kuota bertambah
 * Saat ditolak: 429 Too Many Requests + `Retry-After` (detik).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, RateLimiter> limiterByClient;

    public RateLimitFilter(Map<String, RateLimiter> limiterByClient) {
        this.limiterByClient = limiterByClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        RateLimiter limiter = limiterByClient.get(String.valueOf(req.getAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE)));
        if (limiter == null) {
            chain.doFilter(req, res);
            return;
        }
        RateLimiter.Decision d = limiter.tryAcquire();
        res.setHeader("X-RateLimit-Limit", String.valueOf(limiter.limit()));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(d.getRemaining()));
        res.setHeader("X-RateLimit-Reset", String.valueOf(d.getResetSeconds()));

        if (!d.isAllowed()) {
            res.setHeader("Retry-After", String.valueOf(Math.max(1, d.getResetSeconds())));
            GatewayErrors.write(req, res, 429, "rate-limited",
                    "Kuota " + limiter.algorithm() + " habis, coba lagi dalam " + Math.max(1, d.getResetSeconds()) + " detik");
            return;
        }
        chain.doFilter(req, res);
    }
}
