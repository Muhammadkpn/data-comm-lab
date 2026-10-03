package com.learn.datacomm.rest.v2.store;

public class Account {

    private final String id;
    private final String ownerName;
    private long balance;
    /** Naik setiap saldo berubah -- dipakai sebagai dasar ETag. */
    private long version;

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

    public long getVersion() {
        return version;
    }

    void adjust(long delta) {
        balance += delta;
        version++;
    }
}
