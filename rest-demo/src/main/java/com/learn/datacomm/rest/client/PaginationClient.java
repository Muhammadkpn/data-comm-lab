package com.learn.datacomm.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Membandingkan offset vs cursor pagination saat ada data BARU masuk di tengah-tengah
 * client membaca (kasus nyata: user scroll riwayat mutasi sambil ada transfer masuk).
 *
 * Langkah yang sama untuk kedua gaya:
 *   1. ambil halaman pertama (5 item terbaru)
 *   2. sebuah transfer baru terjadi -> 1 mutasi baru di posisi paling atas
 *   3. ambil halaman kedua
 *
 * Offset: halaman kedua bergeser 1 baris, item terakhir halaman 1 muncul LAGI.
 * Cursor: halaman kedua melanjutkan tepat setelah item terakhir yang sudah dilihat.
 *
 * Jalankan RestDemoApplication dulu.
 */
public class PaginationClient {

    private static final String ACCOUNTS = "http://localhost:8080/v2/accounts/1010001";
    private final RestTemplate http = new RestTemplate();

    public static void main(String[] args) {
        PaginationClient c = new PaginationClient();

        System.out.println("=== OFFSET pagination ===");
        List<String> seen = new ArrayList<>();
        seen.addAll(c.ids(c.http.getForObject(ACCOUNTS + "/transactions?page=0&size=5", JsonNode.class)));
        System.out.println("[CLIENT] page 0: " + seen);
        c.newTransfer("REF-PAGE-OFFSET");
        List<String> page1 = c.ids(c.http.getForObject(ACCOUNTS + "/transactions?page=1&size=5", JsonNode.class));
        System.out.println("[CLIENT] page 1: " + page1);
        report(seen, page1);

        System.out.println("\n=== CURSOR pagination ===");
        JsonNode first = c.http.getForObject(ACCOUNTS + "/transactions/cursor?limit=5", JsonNode.class);
        seen = c.ids(first);
        String cursor = first.get("nextCursor").asText();
        System.out.println("[CLIENT] halaman 1: " + seen + "  nextCursor=" + cursor);
        c.newTransfer("REF-PAGE-CURSOR");
        List<String> next = c.ids(c.http.getForObject(ACCOUNTS + "/transactions/cursor?limit=5&cursor=" + cursor, JsonNode.class));
        System.out.println("[CLIENT] halaman 2: " + next);
        report(seen, next);
    }

    private void newTransfer(String ref) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"sourceAccount\":\"2020002\",\"destinationAccount\":\"1010001\",\"amount\":5000,\"referenceId\":\"" + ref + "\"}";
        http.postForEntity("http://localhost:8080/v2/transfers", new HttpEntity<>(body, h), JsonNode.class);
        System.out.println("[CLIENT] ... sementara itu, transfer masuk ke 1010001 (mutasi baru di urutan teratas)");
    }

    private List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        for (JsonNode tx : page.get("content")) {
            ids.add(tx.get("id").asText());
        }
        return ids;
    }

    private static void report(List<String> before, List<String> after) {
        Set<String> dup = new LinkedHashSet<>(before);
        dup.retainAll(after);
        System.out.println(dup.isEmpty()
                ? "[CLIENT] Tidak ada duplikat -- urutan stabil"
                : "[CLIENT] DUPLIKAT terlihat dua kali: " + dup);
    }
}
