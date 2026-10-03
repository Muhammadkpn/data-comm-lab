package com.learn.datacomm.udp;

/**
 * Jalankan server UDP di port 9092 dengan simulasi 30% paket hilang.
 * Argumen opsional: lossRate (mis. 0 untuk jaringan sempurna).
 */
public class TransferServer {

    public static final int PORT = 9092;

    public static void main(String[] args) throws Exception {
        double lossRate = args.length > 0 ? Double.parseDouble(args[0]) : 0.3;
        UdpTransferServer server = new UdpTransferServer(PORT, lossRate, 42);
        server.start();
        System.out.println("[SERVER] UDP server di port " + PORT + ", simulasi loss " + (int) (lossRate * 100) + "%");
        Thread.currentThread().join();
    }
}
