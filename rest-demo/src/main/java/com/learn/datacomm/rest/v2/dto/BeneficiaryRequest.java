package com.learn.datacomm.rest.v2.dto;

import javax.validation.constraints.NotBlank;

/** Body untuk POST dan PUT /v2/beneficiaries. Hanya accountNumber yang wajib. */
public class BeneficiaryRequest {

    @NotBlank
    private String accountNumber;
    private String alias;
    private String bankCode;

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getBankCode() {
        return bankCode;
    }

    public void setBankCode(String bankCode) {
        this.bankCode = bankCode;
    }
}
