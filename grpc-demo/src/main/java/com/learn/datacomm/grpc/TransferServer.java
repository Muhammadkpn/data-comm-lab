package com.learn.datacomm.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;

/**
 * Entry point server gRPC. gRPC berjalan di atas HTTP/2 (bukan HTTP/1.1
 * seperti REST biasa) -- inilah yang memungkinkan multiplexing beberapa
 * RPC call dalam satu koneksi TCP, dan yang mendasari server streaming.
 */
public class TransferServer {

    private static final int PORT = 9095;

    public static void main(String[] args) throws Exception {
        Server server = ServerBuilder.forPort(PORT)
                .addService(new TransferServiceImpl())
                .build()
                .start();

        System.out.println("[SERVER] gRPC server listening on port " + PORT);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[SERVER] Shutting down gRPC server");
            server.shutdown();
        }));

        server.awaitTermination();
    }
}
