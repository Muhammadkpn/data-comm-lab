package com.learn.datacomm.rest.v2.store;

public class Transfer {

    private final String id;
    private final String referenceId;
    private final String sourceAccount;
    private final String destinationAccount;
    private final long amount;
    private final String status;
    private final String createdAt;

    public Transfer(String id, String referenceId, String sourceAccount, String destinationAccount,
                    long amount, String status, String createdAt) {
        this.id = id;
        this.referenceId = referenceId;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getSourceAccount() {
        return sourceAccount;
    }

    public String getDestinationAccount() {
        return destinationAccount;
    }

    public long getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
