package com.learn.datacomm.gateway.client;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Membandingkan dua algoritma rate limit di gateway (jalankan GatewayDemoApplication dulu).
 *
 *   A. Token bucket (key-mobile: burst 5, isi ulang 1/detik)
 *      10 request sekaligus -> 5 lolos, 5 ditolak 429. Tunggu 2 detik -> 2 token baru.
 *
 *   B. Edge burst pada fixed window (key-partner: 5 per jendela 5 detik)
 *      5 request tepat sebelum jendela berganti + 5 request tepat sesudahnya
 *      -> 10 request lolos dalam < 1 detik, DUA KALI limit yang dimaksud.
 *
 *   C. Pola yang sama dengan B, tapi ke token bucket -> hanya ~5 yang lolos.
 */
public class BurstClient {

    private static final String URL_ECHO = "http://localhost:8084/api/echo/ping";
    private static final long WINDOW_MS = 5_000;

    public static void main(String[] args) throws Exception {
        System.out.println("=== A. Token bucket: 10 request sekaligus ===");
        int ok = burst("key-mobile", 10);
        System.out.println("[CLIENT] lolos " + ok + "/10");
        System.out.println("[CLIENT] ... tunggu 2 detik (bucket terisi 2 token)");
        Thread.sleep(2_000);
        ok = burst("key-mobile", 3);
        System.out.println("[CLIENT] lolos " + ok + "/3");

        System.out.println("\n=== B. Fixed window: burst di TEPI jendela ===");
        System.out.println("[CLIENT] lolos total " + edgeBurst("key-partner") + "/10 dalam < 1 detik  (limit: 5 per 5 detik)");

        Thread.sleep(5_000); // biarkan bucket key-mobile terisi penuh lagi
        System.out.println("\n=== C. Token bucket dengan pola yang sama ===");
        System.out.println("[CLIENT] lolos total " + edgeBurst("key-mobile") + "/10");
    }

    private static int edgeBurst(String key) throws Exception {
        // Jendela fixed window dimulai di kelipatan 5 detik epoch (lihat FixedWindowRateLimiter).
        long boundary = (System.currentTimeMillis() / WINDOW_MS + 1) * WINDOW_MS;
        if (boundary - System.currentTimeMillis() < 600) {
            boundary += WINDOW_MS;
        }
        sleepUntil(boundary - 400);
        System.out.println("[CLIENT] 400 ms SEBELUM jendela berganti:");
        int ok = burst(key, 5);
        sleepUntil(boundary + 50);
        System.out.println("[CLIENT] 50 ms SESUDAH jendela berganti:");
        ok += burst(key, 5);
        return ok;
    }

    private static int burst(String apiKey, int n) throws Exception {
        int ok = 0;
        StringBuilder line = new StringBuilder("[CLIENT]   ");
        String last = "";
        for (int i = 0; i < n; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(URL_ECHO).openConnection();
            c.setRequestProperty("X-API-Key", apiKey);
            int status = c.getResponseCode();
            line.append(status).append(' ');
            if (status == 200) {
                ok++;
            } else if (status == 429) {
                last = "  (Retry-After: " + c.getHeaderField("Retry-After") + "s)";
            }
            if (i == n - 1) {
                line.append(" sisa=").append(c.getHeaderField("X-RateLimit-Remaining"));
            }
            c.disconnect();
        }
        System.out.println(line + last);
        return ok;
    }

    private static void sleepUntil(long epochMillis) throws InterruptedException {
        long wait = epochMillis - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
    }
}
