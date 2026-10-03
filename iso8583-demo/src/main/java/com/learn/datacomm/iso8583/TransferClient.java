package com.learn.datacomm.iso8583;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;

import java.net.Socket;

/**
 * Client ISO 8583 sederhana: bentuk ISOMsg, pack jadi bytes (lewat
 * packager), kirim lewat TCP dengan length-prefix framing, tunggu response,
 * unpack, baca hasilnya per Data Element.
 */
public class TransferClient {

    private static final String HOST = "localhost";
    private static final int PORT = 9096;

    public static void main(String[] args) throws Exception {
        GenericPackager packager = TransferMessageBuilder.loadPackager();

        System.out.println("=== 1. Happy path ===");
        sendTransfer(packager, "1010001", "2020002", 150_000L, "000001");

        System.out.println("\n=== 2. Error case: source == destination (DE39 != 00) ===");
        sendTransfer(packager, "1010001", "1010001", 50_000L, "000002");
    }

    private static void sendTransfer(GenericPackager packager, String sourceAccount, String destinationAccount,
                                       long amount, String referenceId) throws Exception {
        ISOMsg request = TransferMessageBuilder.buildTransferRequest(
                packager, sourceAccount, destinationAccount, amount, referenceId);

        System.out.println("[CLIENT] Mengirim MTI=" + request.getMTI() +
                " DE2=" + sourceAccount + " DE102=" + destinationAccount + " DE4=" + amount);

        // Tampilkan hex dump pesan yang sudah di-pack, supaya kelihatan
        // bahwa ini pesan BINER terstruktur, bukan teks JSON yang gampang
        // dibaca manusia seperti di rest-demo.
        byte[] packed = request.pack();
        System.out.println("[CLIENT] Pesan ter-pack (" + packed.length + " byte), hex: " + toHex(packed));

        try (Socket socket = new Socket(HOST, PORT)) {
            Iso8583Transport.writeMessage(socket.getOutputStream(), request);

            ISOMsg response = Iso8583Transport.readMessage(socket.getInputStream(), packager);
            String responseCode = response.getString(39);

            System.out.println("[CLIENT] Response MTI=" + response.getMTI() + " DE39=" + responseCode +
                    (responseCode.equals("00") ? " (APPROVED)" : " (DITOLAK)"));
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
