package com.learn.datacomm.rest.dto;

/** Response body untuk POST /transfers. Diserialize otomatis jadi JSON oleh Spring. */
public class TransferResponse {

    private String referenceId;
    private String status;
    private String message;

    public TransferResponse() {
    }

    public TransferResponse(String referenceId, String status, String message) {
        this.referenceId = referenceId;
        this.status = status;
        this.message = message;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
