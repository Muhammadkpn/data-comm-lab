package com.learn.datacomm.graphql.store;

import com.learn.datacomm.graphql.model.Account;
import com.learn.datacomm.graphql.model.Transaction;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Database" in-memory sederhana untuk demo -- bukan JPA/repository
 * sungguhan, cukup Map biasa. Fokus modul ini adalah mekanisme GraphQL
 * (schema, resolver, N+1 vs DataLoader), bukan persistence layer.
 */
@Component
public class AccountStore {

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Map<String, List<Transaction>> transactionsByAccount = new LinkedHashMap<>();
    private final AtomicInteger transactionSequence = new AtomicInteger(1);

    public AccountStore() {
        seedAccount("ACC-001", "Andi Wijaya", 5_000_000);
        seedAccount("ACC-002", "Budi Santoso", 3_250_000);
        seedAccount("ACC-003", "Citra Lestari", 7_800_000);
        seedAccount("ACC-004", "Dewi Anggraini", 1_500_000);

        // >5 transaksi per akun supaya skenario "5 transaksi terakhir" di
        // README benar-benar memotong sebagian riwayat, bukan menampilkan semuanya.
        for (String accountId : accounts.keySet()) {
            for (int i = 7; i >= 1; i--) {
                String type = (i % 2 == 0) ? "CREDIT" : "DEBIT";
                long amount = 25_000L * i;
                addTransaction(accountId, type, amount, "REF-SEED-" + accountId + "-" + i,
                        Instant.now().minus(i, ChronoUnit.DAYS).toString());
            }
        }
    }

    private void seedAccount(String id, String ownerName, long balance) {
        accounts.put(id, new Account(id, ownerName, balance));
        transactionsByAccount.put(id, new ArrayList<>());
    }

    private void addTransaction(String accountId, String type, long amount,
                                 String referenceId, String timestamp) {
        String txId = "TX-" + transactionSequence.getAndIncrement();
        transactionsByAccount.get(accountId)
                .add(new Transaction(txId, accountId, type, amount, referenceId, timestamp));
    }

    public Account findById(String id) {
        return accounts.get(id);
    }

    public List<Account> findAll() {
        return new ArrayList<>(accounts.values());
    }

    /** Riwayat transaksi 1 akun, terbaru dulu, dipotong sesuai limit (null = semua). */
    public List<Transaction> findTransactions(String accountId, Integer limit) {
        List<Transaction> all = transactionsByAccount.getOrDefault(accountId, Collections.emptyList());
        List<Transaction> newestFirst = new ArrayList<>(all);
        Collections.reverse(newestFirst);
        if (limit != null && limit < newestFirst.size()) {
            return newestFirst.subList(0, limit);
        }
        return newestFirst;
    }

    /**
     * Proses transfer in-memory: debit source, kredit destination, catat
     * dua Transaction baru. Tidak ada pengecekan saldo minus/lock
     * concurrency -- demo BASIC, bukan business logic realistis.
     */
    public TransferOutcome applyTransfer(String sourceAccountId, String destinationAccountId,
                                          long amount, String referenceId) {
        Account source = accounts.get(sourceAccountId);
        Account destination = accounts.get(destinationAccountId);

        if (source == null || destination == null) {
            return TransferOutcome.rejected("Source atau destination account tidak ditemukan");
        }
        if (sourceAccountId.equals(destinationAccountId)) {
            return TransferOutcome.rejected("Source dan destination account tidak boleh sama");
        }

        source.setBalance(source.getBalance() - amount);
        destination.setBalance(destination.getBalance() + amount);

        String now = Instant.now().toString();
        addTransaction(sourceAccountId, "DEBIT", amount, referenceId, now);
        addTransaction(destinationAccountId, "CREDIT", amount, referenceId, now);

        return TransferOutcome.success(source, destination);
    }

    public static final class TransferOutcome {
        private final boolean success;
        private final String message;
        private final Account sourceAccount;
        private final Account destinationAccount;

        private TransferOutcome(boolean success, String message, Account sourceAccount, Account destinationAccount) {
            this.success = success;
            this.message = message;
            this.sourceAccount = sourceAccount;
            this.destinationAccount = destinationAccount;
        }

        static TransferOutcome success(Account source, Account destination) {
            return new TransferOutcome(true, "Transfer berhasil diproses", source, destination);
        }

        static TransferOutcome rejected(String message) {
            return new TransferOutcome(false, message, null, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Account getSourceAccount() {
            return sourceAccount;
        }

        public Account getDestinationAccount() {
            return destinationAccount;
        }
    }
}
