package com.learn.datacomm.realtime.tracker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Mensimulasikan pemrosesan transfer di background: stage berganti setiap `stepMs`.
 *
 * Inti perbedaan keempat pola ada di SINI: polling MEMBACA state kapan pun client
 * bertanya (`current`), sedangkan long polling / SSE / WebSocket MENDAFTARKAN listener
 * (`subscribe`) yang dipanggil server tepat saat state berubah (push).
 */
@Component
public class TransferTracker {

    private static final String[] STAGES = {"RECEIVED", "VALIDATING", "DEBIT_SOURCE", "CREDIT_DESTINATION", "COMPLETED"};

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, TransferStatus> statuses = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<TransferStatus>>> listeners = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger();
    private final long stepMs;

    public TransferTracker(@Value("${realtime.step-ms:1000}") long stepMs) {
        this.stepMs = stepMs;
    }

    public TransferStatus start() {
        String id = String.format("TRF-%04d", seq.incrementAndGet());
        update(id, 0);
        for (int i = 1; i < STAGES.length; i++) {
            final int stage = i;
            scheduler.schedule(() -> update(id, stage), stepMs * i, TimeUnit.MILLISECONDS);
        }
        return statuses.get(id);
    }

    public TransferStatus current(String id) {
        return statuses.get(id);
    }

    /** Daftarkan listener; kembalikan Runnable untuk berhenti berlangganan. */
    public Runnable subscribe(String id, Consumer<TransferStatus> listener) {
        listeners.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> {
            List<Consumer<TransferStatus>> list = listeners.get(id);
            if (list != null) {
                list.remove(listener);
            }
        };
    }

    private void update(String id, int stageIndex) {
        TransferStatus status = new TransferStatus(id, STAGES[stageIndex], stageIndex + 1, System.currentTimeMillis());
        statuses.put(id, status);
        System.out.println("[SERVER] " + id + " -> " + status.getStage());
        List<Consumer<TransferStatus>> list = listeners.get(id);
        if (list != null) {
            for (Consumer<TransferStatus> l : list) {
                l.accept(status);
            }
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
