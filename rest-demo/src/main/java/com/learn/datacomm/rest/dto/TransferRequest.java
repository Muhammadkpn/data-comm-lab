package com.learn.datacomm.rest.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

/**
 * Request body untuk POST /transfers. Spring otomatis deserialize JSON body
 * jadi object ini (lewat Jackson) berdasarkan nama field -- tidak perlu kita
 * tulis parsing manual seperti di tcp-raw-demo.
 *
 * Anotasi javax.validation (@NotBlank, @Positive) dicek otomatis oleh Spring
 * kalau controller method dianotasi @Valid -- ini "validasi standar" yang
 * jadi salah satu ciri khas REST/Spring dibanding raw TCP.
 */
public class TransferRequest {

    @NotBlank
    private String sourceAccount;

    @NotBlank
    private String destinationAccount;

    @Positive
    private long amount;

    @NotBlank
    private String referenceId;

    public TransferRequest() {
        // required by Jackson untuk deserialisasi JSON
    }

    public TransferRequest(String sourceAccount, String destinationAccount, long amount, String referenceId) {
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.referenceId = referenceId;
    }

    public String getSourceAccount() {
        return sourceAccount;
    }

    public void setSourceAccount(String sourceAccount) {
        this.sourceAccount = sourceAccount;
    }

    public String getDestinationAccount() {
        return destinationAccount;
    }

    public void setDestinationAccount(String destinationAccount) {
        this.destinationAccount = destinationAccount;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }
}
