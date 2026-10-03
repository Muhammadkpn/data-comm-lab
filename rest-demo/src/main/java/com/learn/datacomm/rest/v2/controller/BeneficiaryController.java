package com.learn.datacomm.rest.v2.controller;

import com.learn.datacomm.rest.v2.dto.Beneficiary;
import com.learn.datacomm.rest.v2.dto.BeneficiaryRequest;
import com.learn.datacomm.rest.v2.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Satu resource, semua method HTTP -- untuk merasakan beda semantiknya:
 *
 *   GET     /v2/beneficiaries          list           safe, idempotent, cacheable
 *   GET     /v2/beneficiaries/{id}     ambil satu     safe, idempotent
 *   HEAD    /v2/beneficiaries/{id}     cek ada/tidak  sama seperti GET tanpa body (otomatis oleh Spring)
 *   POST    /v2/beneficiaries          buat baru      TIDAK idempotent: 2x POST = 2 resource
 *   PUT     /v2/beneficiaries/{id}     ganti TOTAL    idempotent; field yang tidak dikirim jadi null
 *   PATCH   /v2/beneficiaries/{id}     ubah sebagian  hanya field yang dikirim yang berubah
 *   DELETE  /v2/beneficiaries/{id}     hapus          idempotent: state akhir sama walau diulang
 *   OPTIONS /v2/beneficiaries/{id}     method apa saja yang boleh (header Allow, otomatis oleh Spring)
 *
 * Catatan DELETE: "idempotent" bicara soal STATE server, bukan status code. DELETE
 * kedua menjawab 404 (sudah tidak ada) -- tetap idempotent karena hasil akhirnya sama.
 */
@RestController
@RequestMapping("/v2/beneficiaries")
public class BeneficiaryController {

    /** RFC 7396 JSON Merge Patch: format standar untuk body PATCH. */
    public static final String MERGE_PATCH_JSON = "application/merge-patch+json";
    private static final Set<String> PATCHABLE = new HashSet<>(Arrays.asList("accountNumber", "alias", "bankCode"));

    private final Map<String, Beneficiary> store = new LinkedHashMap<>();
    private final AtomicInteger seq = new AtomicInteger();

    @GetMapping
    public synchronized List<Beneficiary> list() {
        return new ArrayList<>(store.values());
    }

    @GetMapping("/{id}")
    public synchronized Beneficiary get(@PathVariable String id) {
        return find(id);
    }

    @PostMapping
    public synchronized ResponseEntity<Beneficiary> create(@Valid @RequestBody BeneficiaryRequest req) {
        String id = "BNF-" + seq.incrementAndGet();
        Beneficiary created = new Beneficiary(id, req.getAccountNumber(), req.getAlias(), req.getBankCode());
        store.put(id, created);
        System.out.println("[SERVER] POST -> 201, resource baru " + id);
        return ResponseEntity.created(URI.create("/v2/beneficiaries/" + id)).body(created);
    }

    @PutMapping("/{id}")
    public synchronized Beneficiary replace(@PathVariable String id, @Valid @RequestBody BeneficiaryRequest req) {
        find(id);
        // PUT = kirim representasi LENGKAP. Field yang tidak ada di body sengaja ikut
        // jadi null -- inilah beda utamanya dengan PATCH.
        Beneficiary replaced = new Beneficiary(id, req.getAccountNumber(), req.getAlias(), req.getBankCode());
        store.put(id, replaced);
        System.out.println("[SERVER] PUT " + id + " -> diganti total");
        return replaced;
    }

    @PatchMapping(value = "/{id}", consumes = {MERGE_PATCH_JSON, MediaType.APPLICATION_JSON_VALUE})
    public synchronized Beneficiary patch(@PathVariable String id, @RequestBody Map<String, Object> changes) {
        Beneficiary current = find(id);
        for (String field : changes.keySet()) {
            if (!PATCHABLE.contains(field)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "unknown-field", "Field " + field + " tidak bisa di-patch");
            }
        }
        // Merge Patch: field yang dikirim -> diganti; dikirim null -> dihapus; tidak dikirim -> tetap.
        String accountNumber = merged(changes, "accountNumber", current.getAccountNumber());
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation-error", "accountNumber tidak boleh kosong");
        }
        Beneficiary patched = new Beneficiary(id, accountNumber,
                merged(changes, "alias", current.getAlias()),
                merged(changes, "bankCode", current.getBankCode()));
        store.put(id, patched);
        System.out.println("[SERVER] PATCH " + id + " -> field diubah: " + changes.keySet());
        return patched;
    }

    @DeleteMapping("/{id}")
    public synchronized ResponseEntity<Void> delete(@PathVariable String id) {
        find(id);
        store.remove(id);
        System.out.println("[SERVER] DELETE " + id + " -> 204");
        return ResponseEntity.noContent().build();
    }

    private Beneficiary find(String id) {
        Beneficiary b = store.get(id);
        if (b == null) {
            throw ApiException.notFound("Beneficiary " + id + " tidak ditemukan");
        }
        return b;
    }

    private static String merged(Map<String, Object> changes, String field, String currentValue) {
        if (!changes.containsKey(field)) {
            return currentValue;
        }
        Object v = changes.get(field);
        return v == null ? null : v.toString();
    }
}
