package com.learn.datacomm.iso8583;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;

import java.net.ServerSocket;
import java.net.Socket;

/**
 * Server ISO 8583 sederhana: terima koneksi, baca satu pesan (request),
 * proses (happy path), kirim response. Model threading: satu thread per
 * koneksi, sama seperti tcp-raw-demo.
 */
public class TransferServer {

    private static final int PORT = 9096;

    public static void main(String[] args) throws Exception {
        GenericPackager packager = TransferMessageBuilder.loadPackager();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[SERVER] ISO 8583 server listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SERVER] Koneksi baru dari " + clientSocket.getRemoteSocketAddress());
                new Thread(() -> handleClient(clientSocket, packager)).start();
            }
        }
    }

    private static void handleClient(Socket socket, GenericPackager packager) {
        try (Socket s = socket) {
            ISOMsg request = Iso8583Transport.readMessage(s.getInputStream(), packager);

            System.out.println("[SERVER] Diterima MTI=" + request.getMTI());
            System.out.println("[SERVER] DE2  (source account)      = " + request.getString(2));
            System.out.println("[SERVER] DE4  (amount)              = " + request.getString(4));
            System.out.println("[SERVER] DE11 (STAN/reference)      = " + request.getString(11));
            System.out.println("[SERVER] DE102 (destination account) = " + request.getString(102));

            String sourceAccount = request.getString(2);
            String destinationAccount = request.getString(102);

            ISOMsg response;
            if (sourceAccount.equals(destinationAccount)) {
                // Error case yang penting ditunjukkan: ISO 8583 punya
                // response code STANDAR (DE 39) untuk macam-macam kondisi
                // gagal, beda dengan REST yang bebas pakai HTTP status +
                // body pesan sendiri. "96" = System Malfunction dipilih di
                // sini sekadar contoh; kode riil tergantung spesifikasi
                // switching yang dipakai (bisa beda tiap institusi).
                System.out.println("[SERVER] Ditolak: source == destination -> DE39=96");
                response = TransferMessageBuilder.buildTransferResponse(request, "96",
                        "Source dan destination account tidak boleh sama");
            } else {
                System.out.println("[SERVER] Transfer diproses -> DE39=00 (approved)");
                response = TransferMessageBuilder.buildTransferResponse(request, "00",
                        "Transfer berhasil diproses");
            }

            System.out.println("[SERVER] Mengirim response MTI=" + response.getMTI() +
                    ", DE39=" + response.getString(39));
            Iso8583Transport.writeMessage(s.getOutputStream(), response);

        } catch (Exception e) {
            System.out.println("[SERVER] Gagal menangani koneksi: " + e.getMessage());
        }
    }
}
