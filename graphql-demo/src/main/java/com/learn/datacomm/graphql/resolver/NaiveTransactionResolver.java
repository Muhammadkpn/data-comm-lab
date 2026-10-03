package com.learn.datacomm.graphql.resolver;

import com.learn.datacomm.graphql.model.Account;
import com.learn.datacomm.graphql.model.Transaction;
import com.learn.datacomm.graphql.store.AccountStore;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ==== CONTOH N+1 PROBLEM (SENGAJA DIBIARKAN NAIF) ====
 *
 * @SchemaMapping menghubungkan method ini ke field "transactionsNaive" di
 * type Account -- BUKAN ke Query/Mutation, tapi ke field di dalam sebuah
 * type. Bedanya krusial: kalau field ini muncul di dalam sebuah LIST
 * (misalnya query "accounts { transactionsNaive { ... } }"), GraphQL
 * executor memanggil method ini SEKALI PER ITEM di list itu -- executor
 * tidak tahu dan tidak peduli bahwa item-item itu bisa diambil datanya
 * sekaligus dalam satu query batch.
 *
 * KENAPA N+1 TERJADI:
 *   1 query untuk ambil daftar akun (accounts)
 *   + N query tambahan, satu per akun, untuk ambil transactions-nya
 *   = N+1 total pemanggilan "query" ke data source.
 *
 * Di aplikasi nyata dengan database sungguhan, N+1 ini berarti N query
 * SQL terpisah alih-alih 1 query "WHERE account_id IN (...)" -- makin
 * banyak akun di halaman, makin lambat responsnya, padahal secara
 * response GraphQL-nya terlihat sama sekali tidak berbeda dari yang
 * sudah dioptimalkan.
 *
 * Counter di bawah ini membuktikannya lewat log: coba query
 * "accounts { id transactionsNaive { amount } }" untuk 4 akun dan lihat
 * baris [N+1-NAIVE] muncul 4 kali di console server, satu per akun.
 * Bandingkan dengan TransactionDataLoader yang menjawab field
 * "transactions" (schema sama, resolver beda) hanya dengan 1 baris log
 * batch untuk ke-4 akun sekaligus.
 */
@Controller
public class NaiveTransactionResolver {

    private final AccountStore accountStore;
    private final AtomicInteger callCounter = new AtomicInteger(0);

    public NaiveTransactionResolver(AccountStore accountStore) {
        this.accountStore = accountStore;
    }

    @SchemaMapping(typeName = "Account", field = "transactionsNaive")
    public List<Transaction> transactionsNaive(Account account) {
        int callNumber = callCounter.incrementAndGet();
        System.out.println("[N+1-NAIVE] Pemanggilan #" + callNumber +
                " -- query terpisah untuk accountId=" + account.getId());
        return accountStore.findTransactions(account.getId(), null);
    }
}
