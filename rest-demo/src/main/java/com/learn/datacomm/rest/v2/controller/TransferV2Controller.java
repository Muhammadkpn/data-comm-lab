package com.learn.datacomm.rest.v2.controller;

import com.learn.datacomm.rest.dto.TransferRequest;
import com.learn.datacomm.rest.v2.idempotency.IdempotencyStore;
import com.learn.datacomm.rest.v2.store.BankStore;
import com.learn.datacomm.rest.v2.store.Transfer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.net.URI;

/**
 * Versi 2 dari endpoint transfer. Dibanding `/transfers` (v1):
 *
 *   - Stateful: saldo benar-benar berubah, jadi efek retry ganda KELIHATAN.
 *   - Mendukung header `Idempotency-Key` supaya retry aman.
 *   - 201 Created disertai header `Location` ke resource yang baru dibuat
 *     (id-nya dibuat server: TRF-0001, bukan referenceId kiriman client).
 *   - Semua error memakai `application/problem+json`.
 */
@RestController
@RequestMapping("/v2/transfers")
public class TransferV2Controller {

    private final BankStore bankStore;
    private final IdempotencyStore idempotencyStore;

    public TransferV2Controller(BankStore bankStore, IdempotencyStore idempotencyStore) {
        this.bankStore = bankStore;
        this.idempotencyStore = idempotencyStore;
    }

    @PostMapping
    public ResponseEntity<Transfer> create(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            // HANYA UNTUK DEMO: memperlambat server supaya client bisa mengalami timeout
            // lalu retry. Jangan pernah ada header seperti ini di API sungguhan.
            @RequestHeader(value = "X-Simulate-Delay-Ms", defaultValue = "0") long delayMs) {

        System.out.println("[SERVER] POST /v2/transfers ref=" + request.getReferenceId() +
                " amount=" + request.getAmount() +
                " Idempotency-Key=" + (idempotencyKey == null ? "(tidak ada)" : idempotencyKey));

        if (idempotencyKey == null) {
            // Tanpa key server tidak bisa membedakan "retry" dari "transfer baru yang kebetulan sama".
            Transfer transfer = process(request, delayMs);
            return created(transfer, false);
        }

        IdempotencyStore.Result<Transfer> result = idempotencyStore.execute(
                idempotencyKey, fingerprint(request), () -> process(request, delayMs));
        if (result.isReplayed()) {
            System.out.println("[SERVER] Key sudah pernah diproses -> kembalikan hasil lama, saldo TIDAK didebit lagi");
        }
        return created(result.getValue(), result.isReplayed());
    }

    @GetMapping("/{id}")
    public Transfer get(@PathVariable String id) {
        return bankStore.findTransfer(id);
    }

    private Transfer process(TransferRequest request, long delayMs) {
        Transfer transfer = bankStore.transfer(request.getSourceAccount(), request.getDestinationAccount(),
                request.getAmount(), request.getReferenceId());
        System.out.println("[SERVER] Debit/kredit selesai -> " + transfer.getId());
        // Delay SETELAH saldo berubah: meniru kasus paling berbahaya, yaitu server
        // sudah memproses tapi response-nya tidak sampai ke client (timeout).
        sleep(delayMs);
        return transfer;
    }

    private static ResponseEntity<Transfer> created(Transfer transfer, boolean replayed) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/v2/transfers/" + transfer.getId()))
                .header("Idempotent-Replayed", String.valueOf(replayed))
                .body(transfer);
    }

    private static String fingerprint(TransferRequest r) {
        return r.getSourceAccount() + "|" + r.getDestinationAccount() + "|" + r.getAmount() + "|" + r.getReferenceId();
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
