package com.learn.datacomm.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Mensimulasikan kasus paling berbahaya di API pembayaran: server SUDAH mendebit,
 * tapi response-nya tidak sampai ke client (timeout), lalu client melakukan retry.
 *
 *   Skenario A -- retry TANPA Idempotency-Key  -> saldo terdebit DUA kali
 *   Skenario B -- retry DENGAN Idempotency-Key -> retry terlalu cepat dijawab 409,
 *                 retry berikutnya dapat hasil yang sama, saldo terdebit SEKALI
 *   Skenario C -- key sama dipakai untuk isi request berbeda -> 422
 *
 * Jalankan RestDemoApplication dulu.
 */
public class IdempotencyClient {

    private static final String BASE = "http://localhost:8080/v2";
    private static final String SOURCE = "1010001";
    private static final long AMOUNT = 100_000;

    public static void main(String[] args) throws Exception {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(1_000); // client hanya sabar 1 detik
        RestTemplate http = new RestTemplate(factory);

        System.out.println("=== A. Retry TANPA Idempotency-Key ===");
        long before = balance(http);
        attempt(http, null, "REF-IDEM-A", 2_000);   // timeout, padahal server tetap memproses
        Thread.sleep(1_500);
        attempt(http, null, "REF-IDEM-A", 0);       // retry "aman"... menurut client
        long after = balance(http);
        System.out.println("[CLIENT] Saldo berkurang " + (before - after) + " -- seharusnya " + AMOUNT +
                ((before - after) == 2 * AMOUNT ? "  << DOUBLE DEBIT!" : ""));

        System.out.println("\n=== B. Retry DENGAN Idempotency-Key ===");
        String key = UUID.randomUUID().toString();
        before = balance(http);
        attempt(http, key, "REF-IDEM-B", 2_000);    // timeout lagi
        attempt(http, key, "REF-IDEM-B", 0);        // retry langsung: server masih memproses -> 409
        Thread.sleep(1_500);
        attempt(http, key, "REF-IDEM-B", 0);        // retry setelah jeda: dapat hasil yang sama
        after = balance(http);
        System.out.println("[CLIENT] Saldo berkurang " + (before - after) +
                ((before - after) == AMOUNT ? "  << tepat sekali, aman" : ""));

        System.out.println("\n=== C. Key yang sama dipakai untuk request lain ===");
        attempt(http, key, "REF-IDEM-LAIN", 0);
    }

    private static void attempt(RestTemplate http, String key, String referenceId, long serverDelayMs) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        headers.set("X-Simulate-Delay-Ms", String.valueOf(serverDelayMs));
        String body = "{\"sourceAccount\":\"" + SOURCE + "\",\"destinationAccount\":\"2020002\"," +
                "\"amount\":" + AMOUNT + ",\"referenceId\":\"" + referenceId + "\"}";

        System.out.print("[CLIENT] POST /v2/transfers ref=" + referenceId +
                (key == null ? "" : " key=" + key.substring(0, 8) + "...") + " -> ");
        try {
            ResponseEntity<JsonNode> res = http.postForEntity(BASE + "/transfers", new HttpEntity<>(body, headers), JsonNode.class);
            System.out.println(res.getStatusCodeValue() + " " + res.getBody().get("id").asText() +
                    " (Idempotent-Replayed: " + res.getHeaders().getFirst("Idempotent-Replayed") + ")");
        } catch (ResourceAccessException e) {
            System.out.println("TIMEOUT -- client tidak tahu apakah transfer terjadi atau tidak");
        } catch (HttpStatusCodeException e) {
            System.out.println(e.getRawStatusCode() + " " + e.getResponseBodyAsString());
        }
    }

    private static long balance(RestTemplate http) {
        JsonNode account = http.getForObject(BASE + "/accounts/" + SOURCE, JsonNode.class);
        long b = account.get("balance").asLong();
        System.out.println("[CLIENT] Saldo " + SOURCE + " = " + b);
        return b;
    }
}
