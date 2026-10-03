package com.learn.datacomm.ofs;

import java.net.Socket;

/**
 * Client OFS: mengirim transfer yang SAMA dalam dua format berbeda
 * (native lalu XML) supaya perbedaannya bisa langsung dibandingkan --
 * baik dari ukuran payload maupun struktur pesannya.
 */
public class TransferClient {

    private static final String HOST = "localhost";
    private static final int PORT = 9097;

    public static void main(String[] args) throws Exception {
        TransferMessage request1 = new TransferMessage("1010001", "2020002", 150_000L, "REF-OFS-0001");
        System.out.println("=== 1. Format NATIVE ===");
        sendNative(request1);

        TransferMessage request2 = new TransferMessage("1010001", "2020002", 150_000L, "REF-OFS-0002");
        System.out.println("\n=== 2. Format XML ===");
        sendXml(request2);
    }

    private static void sendNative(TransferMessage request) throws Exception {
        String rawRequest = NativeFormatCodec.encodeRequest(request);
        System.out.println("[CLIENT] Payload native (" + rawRequest.length() + " karakter):");
        System.out.println("[CLIENT] " + rawRequest);

        try (Socket socket = new Socket(HOST, PORT)) {
            OfsTransport.writeMessage(socket.getOutputStream(), rawRequest);
            String rawResponse = OfsTransport.readMessage(socket.getInputStream());

            String[] response = NativeFormatCodec.decodeResponse(rawResponse);
            System.out.println("[CLIENT] Response mentah: " + rawResponse);
            System.out.println("[CLIENT] Status: " + response[1] + " -- " + response[2]);
        }
    }

    private static void sendXml(TransferMessage request) throws Exception {
        String rawRequest = XmlFormatCodec.encodeRequest(request);
        System.out.println("[CLIENT] Payload XML (" + rawRequest.length() + " karakter):");
        System.out.println(rawRequest);

        try (Socket socket = new Socket(HOST, PORT)) {
            OfsTransport.writeMessage(socket.getOutputStream(), rawRequest);
            String rawResponse = OfsTransport.readMessage(socket.getInputStream());

            String[] response = XmlFormatCodec.decodeResponse(rawResponse);
            System.out.println("[CLIENT] Response mentah:");
            System.out.println(rawResponse);
            System.out.println("[CLIENT] Status: " + response[1] + " -- " + response[2]);
        }
    }
}
