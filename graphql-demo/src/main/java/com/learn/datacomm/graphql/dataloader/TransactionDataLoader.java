package com.learn.datacomm.graphql.dataloader;

import com.learn.datacomm.graphql.model.Transaction;
import com.learn.datacomm.graphql.store.AccountStore;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * ==== VERSI YANG SUDAH DIPERBAIKI: DataLoader (batching) ====
 *
 * BatchLoaderRegistry adalah tempat mendaftarkan fungsi batch: alih-alih
 * "ambil transaksi untuk 1 accountId", fungsi ini menerima Set<String>
 * berisi SEMUA accountId yang diminta dalam request yang sama, dan
 * mengembalikan hasilnya sekaligus sebagai satu Map.
 *
 * BAGAIMANA INI MENYELESAIKAN N+1:
 * Saat GraphQL executor memproses field "transactions" untuk tiap Account
 * di dalam list, alih-alih langsung menjalankan query, ia memanggil
 * dataLoader.load(accountId) yang HANYA mendaftarkan accountId itu ke
 * antrean -- belum benar-benar mengambil datanya. Setelah semua field di
 * level itu selesai "mendaftar" (biasanya dalam satu tick event loop),
 * barulah executor men-dispatch SATU pemanggilan batch berisi seluruh
 * accountId yang terkumpul. Hasilnya: N akun tetap 1 pemanggilan ke data
 * source, bukan N pemanggilan seperti NaiveTransactionResolver.
 *
 * Resolver field "transactions" ada di TransactionResolver -- ia
 * menerima parameter bertipe DataLoader<String, List<Transaction>> yang
 * di-inject otomatis oleh Spring for GraphQL berdasarkan nama loader ini
 * ("transactionLoader"), lalu memanggil load(accountId) untuk mendaftarkan
 * permintaan ke batch alih-alih query langsung.
 */
@Component
public class TransactionDataLoader {

    private static final String LOADER_NAME = "transactionLoader";

    private final AtomicInteger batchCallCounter = new AtomicInteger(0);

    public TransactionDataLoader(BatchLoaderRegistry registry, AccountStore accountStore) {
        registry.<String, List<Transaction>>forName(LOADER_NAME).registerMappedBatchLoader((accountIds, env) -> {
            int callNumber = batchCallCounter.incrementAndGet();
            System.out.println("[DATALOADER-BATCH] Pemanggilan #" + callNumber +
                    " -- 1 query batch untuk " + accountIds.size() +
                    " akun sekaligus: " + accountIds);

            Map<String, List<Transaction>> result = accountIds.stream()
                    .collect(Collectors.toMap(
                            accountId -> accountId,
                            accountId -> accountStore.findTransactions(accountId, null)));

            return Mono.just(result);
        });
    }

    public static String loaderName() {
        return LOADER_NAME;
    }
}
