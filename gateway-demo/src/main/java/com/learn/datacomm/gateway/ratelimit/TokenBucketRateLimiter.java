package com.learn.datacomm.gateway.ratelimit;

import java.util.function.LongSupplier;

/**
 * TOKEN BUCKET -- algoritma rate limiting yang paling umum (AWS, Stripe, Kong).
 *
 * Ember berisi maksimal `capacity` token. Setiap request mengambil 1 token; token terisi
 * kembali `refillPerSecond` per detik. Akibatnya:
 *   - Burst singkat diizinkan sampai `capacity` (user membuka app -> beberapa request sekaligus).
 *   - Rata-rata jangka panjang dibatasi `refillPerSecond`.
 *   - Tidak ada "tepi window" yang bisa dieksploitasi (beda dengan fixed window).
 *
 * Token dihitung ulang secara malas (lazy) saat ada request, jadi tidak butuh timer.
 * Di sistem terdistribusi, state (tokens, lastRefill) disimpan di Redis dan diperbarui
 * atomik lewat Lua script supaya semua instance gateway berbagi kuota yang sama.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final int capacity;
    private final double refillPerSecond;
    private final LongSupplier clock;
    private double tokens;
    private long lastRefillNanos;

    public TokenBucketRateLimiter(int capacity, double refillPerSecond) {
        this(capacity, refillPerSecond, System::nanoTime);
    }

    TokenBucketRateLimiter(int capacity, double refillPerSecond, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.clock = nanoClock;
        this.tokens = capacity;
        this.lastRefillNanos = nanoClock.getAsLong();
    }

    @Override
    public synchronized Decision tryAcquire() {
        long now = clock.getAsLong();
        tokens = Math.min(capacity, tokens + (now - lastRefillNanos) / 1e9 * refillPerSecond);
        lastRefillNanos = now;

        if (tokens >= 1) {
            tokens -= 1;
            return new Decision(true, (int) tokens, secondsUntilNextToken());
        }
        return new Decision(false, 0, secondsUntilNextToken());
    }

    private long secondsUntilNextToken() {
        double missing = Math.max(0, 1 - tokens);
        return (long) Math.ceil(missing / refillPerSecond);
    }

    @Override
    public int limit() {
        return capacity;
    }

    @Override
    public String algorithm() {
        return "token-bucket(capacity=" + capacity + ", refill=" + refillPerSecond + "/s)";
    }
}
