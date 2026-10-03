package com.learn.datacomm.graphql.model;

/**
 * Response untuk mutation transfer. sourceAccount/destinationAccount
 * disertakan langsung supaya client bisa ambil saldo terbaru kedua akun
 * dalam SATU round-trip, tanpa perlu query/GET terpisah setelahnya.
 */
public class TransferResult {

    private final String referenceId;
    private final String status;
    private final String message;
    private final Account sourceAccount;
    private final Account destinationAccount;

    public TransferResult(String referenceId, String status, String message,
                           Account sourceAccount, Account destinationAccount) {
        this.referenceId = referenceId;
        this.status = status;
        this.message = message;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getStatus() {
        return status;
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
