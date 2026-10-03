package com.learn.datacomm.rest.v2.store;

import com.learn.datacomm.rest.v2.error.ApiException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pengganti database untuk API v2: rekening, mutasi, dan transfer di memori.
 * Semua method `synchronized` -- cukup untuk demo; di dunia nyata ini tugas
 * transaksi database (row lock / optimistic locking).
 */
@Component
public class BankStore {

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Map<String, Transfer> transfers = new LinkedHashMap<>();
    /** Semua mutasi, urut seq naik (paling lama di depan). */
    private final List<Transaction> transactions = new ArrayList<>();
    private long transactionSeq = 0;
    private long transferSeq = 0;

    public BankStore() {
        reset();
    }

    /** Kembalikan ke data awal. Dipakai saat startup dan oleh test. */
    public synchronized void reset() {
        accounts.clear();
        transfers.clear();
        transactions.clear();
        transactionSeq = 0;
        transferSeq = 0;

        accounts.put("1010001", new Account("1010001", "Andi Wijaya", 10_000_000));
        accounts.put("2020002", new Account("2020002", "Budi Santoso", 5_000_000));
        accounts.put("3030003", new Account("3030003", "Citra Lestari", 7_500_000));

        // 12 mutasi historis untuk rekening 1010001 supaya pagination ada isinya.
        Instant base = Instant.now().minus(30, ChronoUnit.DAYS);
        for (int i = 1; i <= 12; i++) {
            addTransaction("1010001", i % 2 == 0 ? "CREDIT" : "DEBIT", 10_000L * i, "SEED-" + i,
                    base.plus(i, ChronoUnit.DAYS).toString());
        }
    }

    public synchronized Account findAccount(String id) {
        Account account = accounts.get(id);
        if (account == null) {
            throw ApiException.notFound("Rekening " + id + " tidak ditemukan");
        }
        return account;
    }

    public synchronized Transfer findTransfer(String id) {
        Transfer transfer = transfers.get(id);
        if (transfer == null) {
            throw ApiException.notFound("Transfer " + id + " tidak ditemukan");
        }
        return transfer;
    }

    public synchronized Transfer transfer(String source, String destination, long amount, String referenceId) {
        Account src = findAccount(source);
        Account dst = findAccount(destination);

        if (source.equals(destination)) {
            throw ApiException.businessRule("same-account", "Source dan destination account tidak boleh sama");
        }
        if (src.getBalance() < amount) {
            throw ApiException.businessRule("insufficient-balance",
                    "Saldo rekening " + source + " tidak cukup");
        }

        String transferId = String.format("TRF-%04d", ++transferSeq);
        String now = Instant.now().toString();

        src.adjust(-amount);
        dst.adjust(amount);
        addTransaction(source, "DEBIT", amount, transferId, now);
        addTransaction(destination, "CREDIT", amount, transferId, now);

        Transfer transfer = new Transfer(transferId, referenceId, source, destination, amount, "SUCCESS", now);
        transfers.put(transferId, transfer);
        return transfer;
    }

    /** Mutasi satu rekening, paling BARU di depan (urutan yang lazim di aplikasi bank). */
    public synchronized List<Transaction> transactionsNewestFirst(String accountId) {
        findAccount(accountId);
        List<Transaction> result = new ArrayList<>();
        for (Transaction tx : transactions) {
            if (tx.getAccountId().equals(accountId)) {
                result.add(tx);
            }
        }
        Collections.reverse(result);
        return result;
    }

    private void addTransaction(String accountId, String type, long amount, String transferId, String timestamp) {
        transactions.add(new Transaction(++transactionSeq, accountId, type, amount, transferId, timestamp));
    }
}
