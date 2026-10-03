package com.learn.datacomm.rest.client;

import com.learn.datacomm.rest.dto.TransferRequest;
import com.learn.datacomm.rest.dto.TransferResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Client REST sederhana pakai RestTemplate (bagian dari Spring, tidak perlu
 * dependency tambahan). Dijalankan sebagai plain Java main() -- BUKAN Spring
 * Boot application -- untuk menunjukkan bahwa client HTTP itu sendiri tidak
 * butuh keterlibatan framework server sama sekali, cukup tahu URL + format
 * JSON yang disepakati (kontrak API).
 *
 * Jalankan RestDemoApplication dulu sebelum menjalankan client ini.
 */
public class TransferClient {

    private static final String BASE_URL = "http://localhost:8080/transfers";

    public static void main(String[] args) {
        RestTemplate restTemplate = new RestTemplate();

        System.out.println("=== 1. Happy path: transfer valid ===");
        callTransfer(restTemplate, new TransferRequest("1010001", "2020002", 150_000L, "REF-REST-0001"));

        System.out.println("\n=== 2. Business rule rejection: source == destination (-> 422) ===");
        callTransfer(restTemplate, new TransferRequest("1010001", "1010001", 50_000L, "REF-REST-0002"));

        System.out.println("\n=== 3. Validation error: amount negatif (-> 400, ditolak sebelum masuk logic) ===");
        callTransfer(restTemplate, new TransferRequest("1010001", "2020002", -100L, "REF-REST-0003"));

        System.out.println("\n=== 4. GET transfer yang ada -> 200 ===");
        try {
            TransferResponse found = restTemplate.getForObject(BASE_URL + "/REF-REST-0001", TransferResponse.class);
            System.out.println("[CLIENT] Ditemukan: " + found.getStatus() + " - " + found.getMessage());
        } catch (HttpClientErrorException e) {
            System.out.println("[CLIENT] Error: " + e.getStatusCode());
        }

        System.out.println("\n=== 5. GET transfer yang tidak ada -> 404 ===");
        try {
            restTemplate.getForObject(BASE_URL + "/REF-TIDAK-ADA", TransferResponse.class);
        } catch (HttpClientErrorException e) {
            System.out.println("[CLIENT] Error sesuai dugaan: " + e.getStatusCode() + " Not Found");
        }
    }

    private static void callTransfer(RestTemplate restTemplate, TransferRequest request) {
        System.out.println("[CLIENT] POST /transfers -> " +
                request.getSourceAccount() + " -> " + request.getDestinationAccount() +
                ", amount=" + request.getAmount());
        try {
            TransferResponse response = restTemplate.postForObject(BASE_URL, request, TransferResponse.class);
            System.out.println("[CLIENT] 2xx diterima. status=" + response.getStatus() +
                    ", message=" + response.getMessage());
        } catch (HttpClientErrorException e) {
            // RestTemplate melempar exception untuk status 4xx/5xx -- ini pola
            // umum di HTTP client: sukses vs gagal dibedakan lewat status code,
            // bukan lewat parsing manual isi response seperti di raw TCP.
            HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
            System.out.println("[CLIENT] " + status.value() + " " + status.getReasonPhrase() +
                    " -- body: " + e.getResponseBodyAsString());
        }
    }
}
