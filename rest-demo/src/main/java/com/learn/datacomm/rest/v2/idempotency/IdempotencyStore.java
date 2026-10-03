package com.learn.datacomm.rest.v2.idempotency;

import com.learn.datacomm.rest.v2.error.ApiException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Menjamin POST yang di-retry dengan `Idempotency-Key` yang sama hanya diproses SEKALI.
 *
 * Alurnya (pola yang sama dipakai Stripe, Adyen, dsb):
 *
 *   1. Key belum pernah terlihat      -> tandai IN_PROGRESS, proses, simpan hasilnya.
 *   2. Key sudah COMPLETED, body sama -> JANGAN proses lagi, kembalikan hasil tersimpan.
 *   3. Key masih IN_PROGRESS          -> 409: request pertama belum selesai (retry terlalu cepat).
 *   4. Key sama, body BEDA            -> 422: client memakai ulang key untuk operasi lain (bug client).
 *
 * Langkah 1 harus atomik (`putIfAbsent`) -- kalau tidak, dua retry yang datang
 * bersamaan bisa sama-sama lolos cek "belum pernah terlihat" dan dua-duanya mendebit.
 *
 * Batasan demo yang perlu diingat untuk production:
 *   - Disimpan di memori satu instance. Kalau service di-scale ke banyak instance,
 *     store ini harus shared (Redis `SET NX` + TTL, atau tabel DB dengan unique key).
 *   - Tidak ada TTL. Production biasanya menyimpan key 24 jam.
 *   - Key seharusnya di-scope per client (mis. per user/API key), bukan global.
 */
@Component
public class IdempotencyStore {

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    public <T> Result<T> execute(String key, String requestFingerprint, Supplier<T> operation) {
        Entry fresh = new Entry(requestFingerprint);
        Entry existing = entries.putIfAbsent(key, fresh);

        if (existing != null) {
            if (!existing.fingerprint.equals(requestFingerprint)) {
                throw ApiException.businessRule("idempotency-key-reuse",
                        "Idempotency-Key " + key + " sudah dipakai untuk request dengan isi berbeda");
            }
            if (existing.result == null) {
                throw ApiException.conflict("idempotency-in-progress",
                        "Request dengan Idempotency-Key " + key + " masih diproses, coba lagi sebentar");
            }
            @SuppressWarnings("unchecked")
            T stored = (T) existing.result;
            return new Result<>(stored, true);
        }

        try {
            T result = operation.get();
            fresh.result = result;
            return new Result<>(result, false);
        } catch (RuntimeException e) {
            // Gagal (mis. saldo kurang): lepas key supaya client bisa mencoba lagi
            // setelah kondisinya diperbaiki. Yang disimpan hanya hasil SUKSES.
            entries.remove(key, fresh);
            throw e;
        }
    }

    public void clear() {
        entries.clear();
    }

    private static final class Entry {
        private final String fingerprint;
        private volatile Object result;

        private Entry(String fingerprint) {
            this.fingerprint = fingerprint;
        }
    }

    public static final class Result<T> {
        private final T value;
        private final boolean replayed;

        Result(T value, boolean replayed) {
            this.value = value;
            this.replayed = replayed;
        }

        public T getValue() {
            return value;
        }

        /** true kalau hasil diambil dari store, bukan diproses ulang. */
        public boolean isReplayed() {
            return replayed;
        }
    }
}
