package com.learn.datacomm.realtime.client;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mengikuti satu transfer (5 stage, ganti tiap 1 detik) dengan keempat pola, lalu
 * membandingkan: berapa request HTTP yang dibutuhkan, berapa stage yang terlihat,
 * dan berapa lama jeda antara stage berubah di server sampai client melihatnya.
 *
 * Jalankan RealtimeDemoApplication dulu.
 */
public class RealtimeComparisonClient {

    private static final String BASE = "http://localhost:8083";
    private static final Pattern STAGE = Pattern.compile("\"stage\":\"(\\w+)\".*\"version\":(\\d+).*\"changedAt\":(\\d+)");

    public static void main(String[] args) throws Exception {
        List<String> summary = new ArrayList<>();
        summary.add(shortPolling(1_500)); // lebih lambat dari perubahan -> stage terlewat
        summary.add(shortPolling(400));   // lebih cepat dari perubahan -> request sia-sia
        summary.add(longPolling());
        summary.add(sse());
        summary.add(webSocket());

        System.out.println("\n=== Ringkasan (5 stage di server) ===");
        System.out.printf("%-16s %10s %13s %16s%n", "Pola", "Request", "Stage terlihat", "Rata-rata delay");
        summary.forEach(System.out::println);
    }

    private static String shortPolling(long intervalMs) throws Exception {
        System.out.println("\n=== SHORT POLLING (tiap " + intervalMs + " ms) ===");
        String id = startTransfer();
        Stats stats = new Stats();
        while (true) {
            stats.requests++;
            String body = http("GET", "/transfers/" + id + "/status");
            Matcher m = STAGE.matcher(body);
            m.find();
            boolean done = stats.observe(m, "poll #" + stats.requests);
            if (done) {
                break;
            }
            Thread.sleep(intervalMs);
        }
        return stats.row("Polling " + intervalMs + "ms");
    }

    private static String longPolling() throws Exception {
        System.out.println("\n=== LONG POLLING ===");
        String id = startTransfer();
        Stats stats = new Stats();
        int since = 0;
        while (true) {
            stats.requests++;
            String body = http("GET", "/transfers/" + id + "/status/long-poll?since=" + since);
            if (body.isEmpty()) {
                continue; // 204: timeout tanpa perubahan, langsung tanya lagi
            }
            Matcher m = STAGE.matcher(body);
            m.find();
            since = Integer.parseInt(m.group(2));
            if (stats.observe(m, "long-poll #" + stats.requests)) {
                break;
            }
        }
        return stats.row("Long polling");
    }

    private static String sse() throws Exception {
        System.out.println("\n=== SERVER-SENT EVENTS ===");
        String id = startTransfer();
        Stats stats = new Stats();
        stats.requests = 1;
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + "/transfers/" + id + "/events").openConnection();
        conn.setRequestProperty("Accept", "text/event-stream");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    Matcher m = STAGE.matcher(line);
                    m.find();
                    if (stats.observe(m, "event")) {
                        break;
                    }
                }
            }
        }
        return stats.row("SSE");
    }

    private static String webSocket() throws Exception {
        System.out.println("\n=== WEBSOCKET ===");
        String id = startTransfer();
        Stats stats = new Stats();
        stats.requests = 1; // satu HTTP Upgrade, sesudahnya bukan HTTP lagi
        CountDownLatch done = new CountDownLatch(1);

        WebSocketSession session = new StandardWebSocketClient().doHandshake(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                String text = message.getPayload();
                if ("PONG".equals(text)) {
                    System.out.println("[CLIENT] PONG diterima (server juga bisa menerima pesan dari client)");
                    return;
                }
                Matcher m = STAGE.matcher(text);
                if (m.find() && stats.observe(m, "frame")) {
                    done.countDown();
                }
            }
        }, "ws://localhost:8083/ws/transfers").get(5, TimeUnit.SECONDS);

        session.sendMessage(new TextMessage("PING"));
        session.sendMessage(new TextMessage("SUBSCRIBE " + id));
        done.await(15, TimeUnit.SECONDS);
        session.close();
        return stats.row("WebSocket");
    }

    // ---------- helper ----------

    private static String startTransfer() throws Exception {
        Matcher m = Pattern.compile("\"transferId\":\"([\\w-]+)\"").matcher(http("POST", "/transfers"));
        m.find();
        System.out.println("[CLIENT] Transfer dimulai: " + m.group(1));
        return m.group(1);
    }

    private static String http(String method, String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + path).openConnection();
        conn.setRequestMethod(method);
        if (conn.getResponseCode() == 204) {
            return "";
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static final class Stats {
        int requests;
        int lastVersion;
        int stagesSeen;
        long totalDelay;

        /** @return true kalau stage terakhir (COMPLETED) sudah terlihat. */
        synchronized boolean observe(Matcher m, String via) {
            int version = Integer.parseInt(m.group(2));
            long delay = System.currentTimeMillis() - Long.parseLong(m.group(3));
            if (version != lastVersion) {
                if (version > lastVersion + 1) {
                    System.out.println("[CLIENT]   ... " + (version - lastVersion - 1) + " stage TERLEWAT");
                }
                stagesSeen++;
                totalDelay += delay;
                lastVersion = version;
                System.out.printf("[CLIENT] %-14s stage=%-19s terlambat %4d ms%n", via, m.group(1), delay);
            } else {
                System.out.printf("[CLIENT] %-14s (belum berubah -- request sia-sia)%n", via);
            }
            return "COMPLETED".equals(m.group(1));
        }

        String row(String name) {
            return String.format("%-16s %10d %13d %13d ms", name, requests, stagesSeen, totalDelay / Math.max(1, stagesSeen));
        }
    }
}
