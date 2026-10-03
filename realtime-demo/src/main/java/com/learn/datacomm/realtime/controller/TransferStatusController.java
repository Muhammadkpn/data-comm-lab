package com.learn.datacomm.realtime.controller;

import com.learn.datacomm.realtime.tracker.TransferStatus;
import com.learn.datacomm.realtime.tracker.TransferTracker;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tiga pola real-time berbasis HTTP biasa (pola keempat, WebSocket, ada di package ws):
 *
 *   SHORT POLLING  GET /transfers/{id}/status
 *       Client bertanya berulang tiap N detik. Paling sederhana, tapi:
 *       banyak request sia-sia (status belum berubah) DAN update terlambat hingga N detik.
 *
 *   LONG POLLING   GET /transfers/{id}/status/long-poll?since=<version>
 *       Server MENAHAN request sampai ada versi lebih baru dari `since` (atau timeout ->
 *       204). Update hampir instan, request jauh lebih sedikit, tetap HTTP biasa
 *       (lolos proxy/firewall mana pun). Satu update = satu request baru.
 *
 *   SSE            GET /transfers/{id}/events   (Content-Type: text/event-stream)
 *       Satu response HTTP yang tidak pernah "selesai"; server menulis event demi event.
 *       Searah (server -> client), auto-reconnect bawaan browser (EventSource) dengan
 *       header Last-Event-ID supaya event yang terlewat bisa dikirim ulang.
 */
@RestController
@RequestMapping("/transfers")
public class TransferStatusController {

    private final TransferTracker tracker;

    public TransferStatusController(TransferTracker tracker) {
        this.tracker = tracker;
    }

    @PostMapping
    public ResponseEntity<String> start() {
        TransferStatus status = tracker.start();
        return ResponseEntity.status(201).contentType(MediaType.APPLICATION_JSON).body(status.toJson());
    }

    @GetMapping(value = "/{id}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> poll(@PathVariable String id) {
        TransferStatus status = tracker.current(id);
        return status == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(status.toJson());
    }

    @GetMapping(value = "/{id}/status/long-poll", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeferredResult<ResponseEntity<String>> longPoll(@PathVariable String id,
                                                           @RequestParam(defaultValue = "0") int since,
                                                           @RequestParam(defaultValue = "30000") long timeoutMs) {
        // Saat timeout tanpa perubahan: 204, client langsung membuka long-poll berikutnya.
        DeferredResult<ResponseEntity<String>> result =
                new DeferredResult<>(timeoutMs, ResponseEntity.noContent().build());

        if (tracker.current(id) == null) {
            result.setResult(ResponseEntity.notFound().build());
            return result;
        }

        // Thread servlet DILEPAS di sini; response baru dikirim saat setResult dipanggil
        // dari thread lain. Karena itu ribuan long-poll tidak memakan ribuan thread.
        Runnable unsubscribe = tracker.subscribe(id, status -> {
            if (status.getVersion() > since) {
                result.setResult(ResponseEntity.ok(status.toJson()));
            }
        });
        result.onCompletion(unsubscribe);

        // Cek SETELAH subscribe: kalau update terjadi di antara request masuk dan subscribe,
        // update itu tidak boleh hilang.
        TransferStatus current = tracker.current(id);
        if (current.getVersion() > since) {
            result.setResult(ResponseEntity.ok(current.toJson()));
        }
        return result;
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(@PathVariable String id,
                                             @RequestHeader(value = "Last-Event-ID", defaultValue = "0") int lastEventId) {
        if (tracker.current(id) == null) {
            return ResponseEntity.notFound().build();
        }
        SseEmitter emitter = new SseEmitter(60_000L);
        AtomicInteger lastSent = new AtomicInteger(lastEventId);

        Runnable unsubscribe = tracker.subscribe(id, status -> send(emitter, status, lastSent));
        emitter.onCompletion(unsubscribe);
        emitter.onTimeout(unsubscribe);
        emitter.onError(e -> unsubscribe.run());

        // Kirim state saat ini (kecuali client sudah pernah menerimanya -- Last-Event-ID).
        send(emitter, tracker.current(id), lastSent);
        return ResponseEntity.ok(emitter);
    }

    private static void send(SseEmitter emitter, TransferStatus status, AtomicInteger lastSent) {
        synchronized (emitter) {
            if (status.getVersion() <= lastSent.get()) {
                return;
            }
            try {
                // Format di kabel:
                //   id: 3
                //   event: status
                //   data: {"transferId":"TRF-0001","stage":"DEBIT_SOURCE",...}
                //   (baris kosong = akhir event)
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(status.getVersion()))
                        .name("status")
                        .data(status.toJson(), MediaType.APPLICATION_JSON));
                lastSent.set(status.getVersion());
                if (status.isFinal()) {
                    emitter.complete();
                }
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
            }
        }
    }
}
