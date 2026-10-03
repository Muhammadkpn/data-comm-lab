package com.learn.datacomm.rest.v2.store;

public class Transaction {

    /** Urutan global yang naik monoton -- kunci untuk keyset/cursor pagination. */
    private final long seq;
    private final String id;
    private final String accountId;
    private final String type;
    private final long amount;
    private final String transferId;
    private final String timestamp;

    public Transaction(long seq, String accountId, String type, long amount, String transferId, String timestamp) {
        this.seq = seq;
        this.id = String.format("TX-%04d", seq);
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.transferId = transferId;
        this.timestamp = timestamp;
    }

    public long getSeq() {
        return seq;
    }

    public String getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public String getTransferId() {
        return transferId;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
