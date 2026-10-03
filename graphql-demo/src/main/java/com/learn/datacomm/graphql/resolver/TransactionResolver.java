package com.learn.datacomm.graphql.resolver;

import com.learn.datacomm.graphql.model.Account;
import com.learn.datacomm.graphql.model.Transaction;
import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Resolver field "transactions" (versi yang sudah diperbaiki, lawan dari
 * NaiveTransactionResolver di atas). Bedanya dengan versi naif: method ini
 * tidak langsung memanggil AccountStore -- ia hanya mendaftarkan
 * account.getId() ke DataLoader lewat load(), lalu Spring for GraphQL yang
 * mengatur kapan batch-nya benar-benar di-dispatch (lihat penjelasan
 * lengkap N+1 vs batching di TransactionDataLoader).
 *
 * Parameter DataLoader<String, List<Transaction>> di-inject otomatis oleh
 * Spring for GraphQL berdasarkan nama loader "transactionLoader" yang
 * didaftarkan lewat BatchLoaderRegistry.
 *
 * Catatan: argumen "limit" di sini diterapkan SETELAH data batch kembali
 * (per akun), bukan bagian dari key batching -- supaya semua akun tetap
 * bisa di-batch jadi satu pemanggilan walau limit-nya sama untuk semua.
 */
@Controller
public class TransactionResolver {

    @SchemaMapping(typeName = "Account", field = "transactions")
    public CompletableFuture<List<Transaction>> transactions(
            Account account,
            @Argument Integer limit,
            DataLoader<String, List<Transaction>> transactionLoader) {

        return transactionLoader.load(account.getId())
                .thenApply(transactions -> {
                    if (limit != null && limit < transactions.size()) {
                        return transactions.subList(0, limit);
                    }
                    return transactions;
                });
    }
}
