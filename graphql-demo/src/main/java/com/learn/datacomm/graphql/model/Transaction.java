package com.learn.datacomm.graphql.model;

public class Transaction {

    private final String id;
    private final String accountId;
    private final String type; // "DEBIT" atau "CREDIT"
    private final long amount;
    private final String referenceId;
    private final String timestamp;

    public Transaction(String id, String accountId, String type, long amount,
                        String referenceId, String timestamp) {
        this.id = id;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.referenceId = referenceId;
        this.timestamp = timestamp;
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

    public String getReferenceId() {
        return referenceId;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
