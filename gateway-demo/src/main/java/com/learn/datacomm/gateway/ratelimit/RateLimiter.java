package com.learn.datacomm.gateway.ratelimit;

/** Satu instance limiter melayani satu client (dipilih dari API key). */
public interface RateLimiter {

    Decision tryAcquire();

    int limit();

    String algorithm();

    final class Decision {
        private final boolean allowed;
        private final int remaining;
        /** Detik sampai client boleh mencoba lagi / kuota bertambah (dibulatkan ke atas). */
        private final long resetSeconds;

        public Decision(boolean allowed, int remaining, long resetSeconds) {
            this.allowed = allowed;
            this.remaining = remaining;
            this.resetSeconds = resetSeconds;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public int getRemaining() {
            return remaining;
        }

        public long getResetSeconds() {
            return resetSeconds;
        }
    }
}
