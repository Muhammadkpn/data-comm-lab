package com.learn.datacomm.gateway.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Jam palsu supaya perilaku algoritma bisa diuji tanpa sleep. */
class RateLimiterTest {

    @Test
    void tokenBucketMengizinkanBurstLaluMembatasiRataRata() {
        AtomicLong nanos = new AtomicLong();
        TokenBucketRateLimiter bucket = new TokenBucketRateLimiter(5, 1.0, nanos::get);

        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryAcquire().isAllowed());
        }
        RateLimiter.Decision rejected = bucket.tryAcquire();
        assertFalse(rejected.isAllowed());
        assertEquals(1, rejected.getResetSeconds());

        nanos.addAndGet(2_000_000_000L); // 2 detik -> 2 token
        assertTrue(bucket.tryAcquire().isAllowed());
        assertTrue(bucket.tryAcquire().isAllowed());
        assertFalse(bucket.tryAcquire().isAllowed());
    }

    @Test
    void tokenBucketTidakMelebihiKapasitas() {
        AtomicLong nanos = new AtomicLong();
        TokenBucketRateLimiter bucket = new TokenBucketRateLimiter(5, 1.0, nanos::get);
        nanos.addAndGet(60_000_000_000L); // menganggur 1 menit tetap hanya 5 token
        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            if (bucket.tryAcquire().isAllowed()) {
                allowed++;
            }
        }
        assertEquals(5, allowed);
    }

    @Test
    void fixedWindowEdgeBurstMeloloskanDuaKaliLimit() {
        AtomicLong millis = new AtomicLong(4_600); // 400 ms sebelum jendela [5000, 10000)
        FixedWindowRateLimiter window = new FixedWindowRateLimiter(5, 5_000, millis::get);

        int allowed = 0;
        for (int i = 0; i < 5; i++) {
            allowed += window.tryAcquire().isAllowed() ? 1 : 0;
        }
        millis.set(5_050); // 50 ms setelah jendela berganti
        for (int i = 0; i < 5; i++) {
            allowed += window.tryAcquire().isAllowed() ? 1 : 0;
        }
        assertEquals(10, allowed, "10 request dalam 450 ms walau limit 5 per 5 detik");
        assertFalse(window.tryAcquire().isAllowed());
    }

    @Test
    void fixedWindowResetSecondsMenunjukSisaJendela() {
        AtomicLong millis = new AtomicLong(1_200);
        FixedWindowRateLimiter window = new FixedWindowRateLimiter(1, 5_000, millis::get);
        assertEquals(4, window.tryAcquire().getResetSeconds());
    }
}
