package com.learn.datacomm.realtime.tracker;

/** Snapshot status transfer. `version` naik setiap kali stage berubah. */
public class TransferStatus {

    private final String transferId;
    private final String stage;
    private final int version;
    /** Epoch millis saat stage berubah di server -- dipakai client menghitung delay. */
    private final long changedAt;

    public TransferStatus(String transferId, String stage, int version, long changedAt) {
        this.transferId = transferId;
        this.stage = stage;
        this.version = version;
        this.changedAt = changedAt;
    }

    public String getTransferId() {
        return transferId;
    }

    public String getStage() {
        return stage;
    }

    public int getVersion() {
        return version;
    }

    public long getChangedAt() {
        return changedAt;
    }

    public boolean isFinal() {
        return "COMPLETED".equals(stage);
    }

    public String toJson() {
        return "{\"transferId\":\"" + transferId + "\",\"stage\":\"" + stage + "\",\"version\":" + version +
                ",\"changedAt\":" + changedAt + "}";
    }
}
