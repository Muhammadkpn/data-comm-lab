package com.learn.datacomm.graphql.model;

/**
 * Field di sini sengaja hanya id/ownerName/balance -- daftar transaksi
 * TIDAK disimpan sebagai field di object ini, karena diambil lewat resolver
 * terpisah (lihat NaiveTransactionResolver dan TransactionDataLoader).
 * Itulah yang memungkinkan GraphQL executor memanggil resolver transaksi
 * hanya kalau client benar-benar meminta field itu di query.
 */
public class Account {

    private final String id;
    private final String ownerName;
    private long balance;

    public Account(String id, String ownerName, long balance) {
        this.id = id;
        this.ownerName = ownerName;
        this.balance = balance;
    }

    public String getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }
}
