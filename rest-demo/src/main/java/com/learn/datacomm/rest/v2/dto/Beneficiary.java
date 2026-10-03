package com.learn.datacomm.rest.v2.dto;

/** Rekening tujuan favorit yang disimpan user (resource untuk demo method HTTP lengkap). */
public class Beneficiary {

    private final String id;
    private final String accountNumber;
    private final String alias;
    private final String bankCode;

    public Beneficiary(String id, String accountNumber, String alias, String bankCode) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.alias = alias;
        this.bankCode = bankCode;
    }

    public String getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getAlias() {
        return alias;
    }

    public String getBankCode() {
        return bankCode;
    }
}
