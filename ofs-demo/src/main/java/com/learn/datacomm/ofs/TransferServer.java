package com.learn.datacomm.ofs;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Server OFS: mendengarkan di satu port, dan bisa menerima pesan dalam
 * KEDUA format (native maupun XML) di koneksi yang sama. Deteksi format
 * dilakukan sederhana: kalau payload diawali '<', anggap XML; selain itu
 * anggap native format. Ini menunjukkan bahwa native format TIDAK
 * self-describing (makanya perlu heuristik), sementara XML relatif mudah
 * dikenali dari strukturnya sendiri.
 */
public class TransferServer {

    private static final int PORT = 9097;

    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[SERVER] OFS server listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SERVER] Koneksi baru dari " + clientSocket.getRemoteSocketAddress());
                new Thread(() -> handleClient(clientSocket)).start();
            }
        }
    }

    private static void handleClient(Socket socket) {
        try (Socket s = socket) {
            String rawMessage = OfsTransport.readMessage(s.getInputStream());

            boolean isXml = rawMessage.trim().startsWith("<?xml") || rawMessage.trim().startsWith("<OFS_MESSAGE");
            System.out.println("[SERVER] Format terdeteksi: " + (isXml ? "XML" : "NATIVE"));

            TransferMessage request;
            if (isXml) {
                request = XmlFormatCodec.decodeRequest(rawMessage);
            } else {
                request = NativeFormatCodec.decodeRequest(rawMessage);
            }
            System.out.println("[SERVER] Diterima: " + request);

            String status;
            String message;
            if (request.getSourceAccount().equals(request.getDestinationAccount())) {
                status = "REJECTED";
                message = "Source dan destination account tidak boleh sama";
                System.out.println("[SERVER] Ditolak: source == destination");
            } else {
                status = "SUCCESS";
                message = "Transfer berhasil diproses";
                System.out.println("[SERVER] Transfer diproses -> SUCCESS");
            }

            String rawResponse = isXml
                    ? XmlFormatCodec.encodeResponse(request.getReferenceId(), status, message)
                    : NativeFormatCodec.encodeResponse(request.getReferenceId(), status, message);

            OfsTransport.writeMessage(s.getOutputStream(), rawResponse);
            System.out.println("[SERVER] Response terkirim dalam format " + (isXml ? "XML" : "NATIVE"));

        } catch (Exception e) {
            System.out.println("[SERVER] Gagal menangani koneksi: " + e.getMessage());
        }
    }
}
