package com.learn.datacomm.gateway.ratelimit;

import java.util.function.LongSupplier;

/**
 * FIXED WINDOW -- paling sederhana: hitung request per jendela waktu tetap
 * (mis. 5 request per 5 detik, jendela mulai di kelipatan 5 detik), reset di awal jendela.
 * Di Redis cukup `INCR key:<window>` + `EXPIRE`.
 *
 * Kelemahan klasik: EDGE BURST. Client yang mengirim `limit` request di akhir jendela N
 * dan `limit` request lagi di awal jendela N+1 berhasil mengirim 2x limit dalam waktu
 * sangat singkat. Lihat BurstClient skenario B.
 *
 * Perbaikannya: sliding window log/counter (akurat, lebih mahal) atau token bucket.
 */
public class FixedWindowRateLimiter implements RateLimiter {

    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;
    private long currentWindow = -1;
    private int count;

    public FixedWindowRateLimiter(int limit, long windowMillis) {
        this(limit, windowMillis, System::currentTimeMillis);
    }

    FixedWindowRateLimiter(int limit, long windowMillis, LongSupplier millisClock) {
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clock = millisClock;
    }

    @Override
    public synchronized Decision tryAcquire() {
        long now = clock.getAsLong();
        long window = now / windowMillis;
        if (window != currentWindow) {
            currentWindow = window;
            count = 0;
        }
        long resetSeconds = (long) Math.ceil(((window + 1) * windowMillis - now) / 1000.0);
        if (count < limit) {
            count++;
            return new Decision(true, limit - count, resetSeconds);
        }
        return new Decision(false, 0, resetSeconds);
    }

    @Override
    public int limit() {
        return limit;
    }

    @Override
    public String algorithm() {
        return "fixed-window(" + limit + " per " + windowMillis + "ms)";
    }
}
