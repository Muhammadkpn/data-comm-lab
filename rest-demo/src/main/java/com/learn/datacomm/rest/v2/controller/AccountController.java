package com.learn.datacomm.rest.v2.controller;

import com.learn.datacomm.rest.v2.error.ApiException;
import com.learn.datacomm.rest.v2.store.Account;
import com.learn.datacomm.rest.v2.store.BankStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dua topik sekaligus di satu endpoint:
 *
 * 1. HEADER VERSIONING -- URL sama, bentuk response dipilih lewat `X-API-Version`.
 *      v1: "balance": 10000000                         (angka polos)
 *      v2: "balance": {"amount": 10000000, "currency": "IDR"}
 *    Mengubah tipe field dari angka ke object adalah breaking change, jadi butuh versi.
 *    Tanpa header -> v1, supaya client lama yang tidak tahu soal header tetap jalan.
 *
 * 2. CONDITIONAL GET (ETag) -- server mengirim "sidik jari" representasi. Client
 *    menyimpannya dan mengirim balik lewat `If-None-Match`; kalau belum berubah
 *    server menjawab 304 tanpa body (hemat bandwidth, penting untuk mobile).
 *
 * Karena satu URL bisa punya beberapa representasi, response WAJIB menyertakan
 * `Vary: X-API-Version` -- kalau tidak, cache/CDN di tengah jalan bisa menyajikan
 * response v1 ke client yang minta v2.
 */
@RestController
@RequestMapping("/v2/accounts")
public class AccountController {

    private final BankStore bankStore;

    public AccountController(BankStore bankStore) {
        this.bankStore = bankStore;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String id,
            @RequestHeader(value = "X-API-Version", defaultValue = "1") String apiVersion,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        if (!"1".equals(apiVersion) && !"2".equals(apiVersion)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported-version",
                    "X-API-Version " + apiVersion + " tidak didukung (pilih 1 atau 2)");
        }

        Account account = bankStore.findAccount(id);
        // ETag berbeda per versi representasi DAN per perubahan saldo.
        String etag = "\"" + account.getId() + "-" + account.getVersion() + "-v" + apiVersion + "\"";

        if (etag.equals(ifNoneMatch)) {
            System.out.println("[SERVER] GET /v2/accounts/" + id + " If-None-Match cocok -> 304 tanpa body");
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag).header(HttpHeaders.VARY, "X-API-Version").build();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", account.getId());
        body.put("ownerName", account.getOwnerName());
        if ("1".equals(apiVersion)) {
            body.put("balance", account.getBalance());
        } else {
            Map<String, Object> balance = new LinkedHashMap<>();
            balance.put("amount", account.getBalance());
            balance.put("currency", "IDR");
            body.put("balance", balance);
        }

        System.out.println("[SERVER] GET /v2/accounts/" + id + " (X-API-Version=" + apiVersion + ") -> 200 ETag=" + etag);
        return ResponseEntity.ok()
                .eTag(etag)
                .header(HttpHeaders.VARY, "X-API-Version")
                .body(body);
    }
}
