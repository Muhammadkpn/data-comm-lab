package com.learn.datacomm.ofs;

/** Model pesan transfer dana yang dipakai bersama oleh kedua format (native & XML). */
public class TransferMessage {

    private final String sourceAccount;
    private final String destinationAccount;
    private final long amount;
    private final String referenceId;

    public TransferMessage(String sourceAccount, String destinationAccount, long amount, String referenceId) {
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.referenceId = referenceId;
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

    public String getReferenceId() {
        return referenceId;
    }

    @Override
    public String toString() {
        return "TransferMessage{" +
                "sourceAccount='" + sourceAccount + '\'' +
                ", destinationAccount='" + destinationAccount + '\'' +
                ", amount=" + amount +
                ", referenceId='" + referenceId + '\'' +
                '}';
    }
}
