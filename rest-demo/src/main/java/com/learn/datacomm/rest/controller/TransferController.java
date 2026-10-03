package com.learn.datacomm.rest.controller;

import com.learn.datacomm.rest.dto.TransferRequest;
import com.learn.datacomm.rest.dto.TransferResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * ==== INI BAGIAN YANG PALING SPESIFIK KE "REST" ====
 *
 * Dibanding raw TCP (tcp-raw-demo), semua hal berikut ini "gratis" didapat
 * dari HTTP + Spring, tidak perlu kita implementasi manual:
 *   - Framing pesan: Content-Length / chunked encoding diurus HTTP layer.
 *   - Routing: satu server bisa punya banyak "operasi" (endpoint) berbeda,
 *     dibedakan lewat method + path, bukan satu koneksi = satu operasi.
 *   - Serialisasi: JSON <-> Java object otomatis lewat Jackson.
 *   - Status code: cara standar untuk menyatakan hasil (2xx sukses, 4xx
 *     client error, 5xx server error) yang dipahami universal oleh HTTP
 *     client manapun -- beda dengan tcp-raw-demo yang statusnya cuma
 *     string bebas yang kita definisikan sendiri.
 *
 * @Valid pada parameter method men-trigger validasi javax.validation
 * berdasarkan anotasi di TransferRequest (@NotBlank, @Positive). Kalau
 * validasi gagal, Spring otomatis melempar exception dan (secara default)
 * mengembalikan 400 Bad Request -- kita tidak perlu cek manual satu-satu.
 */
@RestController
@RequestMapping("/transfers")
public class TransferController {

    /**
     * Happy path: POST /transfers dengan body valid -> 201 Created.
     * 201 (bukan 200) dipakai karena secara semantik REST, endpoint ini
     * MEMBUAT resource baru (record transfer), bukan sekadar mengambil data.
     */
    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        System.out.println("[SERVER] Menerima request: sourceAccount=" + request.getSourceAccount() +
                ", destinationAccount=" + request.getDestinationAccount() +
                ", amount=" + request.getAmount() +
                ", referenceId=" + request.getReferenceId());

        // Error case yang SENGAJA ditunjukkan: rekening sumber dan tujuan sama.
        // Ini bukan soal validasi format field (itu sudah diurus @Valid),
        // tapi business rule -- dan di REST, business rule error biasanya
        // dipetakan ke 422 Unprocessable Entity (request valid secara
        // sintaks, tapi tidak bisa diproses secara semantik).
        if (request.getSourceAccount().equals(request.getDestinationAccount())) {
            System.out.println("[SERVER] Ditolak: source dan destination account sama -> 422");
            TransferResponse rejected = new TransferResponse(
                    request.getReferenceId(), "REJECTED", "Source dan destination account tidak boleh sama");
            return ResponseEntity.unprocessableEntity().body(rejected);
        }

        System.out.println("[SERVER] Transfer diproses -> 201 Created");
        TransferResponse response = new TransferResponse(
                request.getReferenceId(), "SUCCESS", "Transfer berhasil diproses");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Contoh GET untuk melengkapi gambaran REST: mengambil status transfer
     * berdasarkan referenceId di path. Tidak ada penyimpanan sungguhan --
     * cukup untuk menunjukkan pola "GET by id" dan 404 kalau tidak ketemu.
     */
    @GetMapping("/{referenceId}")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable String referenceId) {
        System.out.println("[SERVER] GET /transfers/" + referenceId);

        if (!"REF-REST-0001".equals(referenceId)) {
            System.out.println("[SERVER] referenceId tidak ditemukan -> 404");
            return ResponseEntity.notFound().build();
        }

        TransferResponse response = new TransferResponse(referenceId, "SUCCESS", "Transfer ditemukan");
        return ResponseEntity.ok(response);
    }
}
