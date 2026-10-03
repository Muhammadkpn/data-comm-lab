package com.learn.datacomm.tcpraw;

/**
 * Model pesan "transfer dana" yang dipakai bersama antara client dan server.
 * Ini murni POJO -- belum ada urusan protokol di sini. Serialisasi ke bytes
 * ada di {@link MessageCodec}, supaya jelas dipisah: "apa isinya" vs
 * "bagaimana cara kirimnya di atas TCP".
 *
 * Pemisahan ini sengaja, dan berguna untuk dilihat sebagai pembanding:
 *
 *   TransferMessage  -> APA yang dikirim (4 field, tanpa tahu soal socket)
 *   MessageCodec     -> BAGAIMANA mengirimnya (framing + encoding ke byte)
 *
 * Di modul lain, dua peran ini juga ada tapi sebagian besar sudah disediakan:
 * di rest-demo, kelas DTO memegang peran "apa" dan Jackson memegang "bagaimana";
 * di grpc-demo, dua-duanya di-generate sekaligus dari file .proto. Di sini
 * keduanya ditulis tangan supaya kelihatan bahwa keduanya memang dua urusan
 * yang berbeda.
 *
 * Perhatikan juga: kelas ini TIDAK menyimpan informasi apa pun soal panjang
 * pesan. Panjang baru dihitung saat encoding (lihat MessageCodec.writeMessage),
 * karena yang menentukan panjang adalah hasil encoding-nya, bukan isinya.
 */
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
