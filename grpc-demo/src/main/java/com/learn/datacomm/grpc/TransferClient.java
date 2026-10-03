package com.learn.datacomm.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * Client gRPC pakai blocking stub -- paling gampang dibaca untuk belajar
 * karena urutan pemanggilannya mirip pemanggilan method biasa, walau di
 * baliknya tetap network call. (gRPC juga punya async stub, tidak dipakai
 * di sini supaya fokus ke perbedaan unary vs streaming dulu.)
 */
public class TransferClient {

    private static final String HOST = "localhost";
    private static final int PORT = 9095;

    public static void main(String[] args) throws InterruptedException {
        // plaintext() dipakai karena ini demo lokal tanpa TLS -- di gRPC
        // production, TLS biasanya default/wajib, beda dengan raw TCP yang
        // sama sekali tidak punya opsi TLS bawaan tanpa implementasi manual.
        ManagedChannel channel = ManagedChannelBuilder.forAddress(HOST, PORT)
                .usePlaintext()
                .build();

        TransferServiceGrpc.TransferServiceBlockingStub stub = TransferServiceGrpc.newBlockingStub(channel);

        System.out.println("=== 1. Unary RPC: happy path ===");
        callUnary(stub, "1010001", "2020002", 150_000L, "REF-GRPC-0001");

        System.out.println("\n=== 2. Unary RPC: error case (source == destination) ===");
        callUnary(stub, "1010001", "1010001", 50_000L, "REF-GRPC-0002");

        System.out.println("\n=== 3. Server streaming RPC: watch transfer progress ===");
        watchProgress(stub, "3030003", "4040004", 200_000L, "REF-GRPC-0003");

        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    private static void callUnary(TransferServiceGrpc.TransferServiceBlockingStub stub,
                                   String source, String destination, long amount, String refId) {
        TransferRequest request = TransferRequest.newBuilder()
                .setSourceAccount(source)
                .setDestinationAccount(destination)
                .setAmount(amount)
                .setReferenceId(refId)
                .build();

        System.out.println("[CLIENT] Mengirim unary request: " + refId);
        try {
            TransferResponse response = stub.transfer(request);
            System.out.println("[CLIENT] Response diterima: status=" + response.getStatus() +
                    ", message=" + response.getMessage());
        } catch (StatusRuntimeException e) {
            // Kode status gRPC (bukan HTTP status code) bisa diperiksa langsung
            // lewat e.getStatus().getCode() -- ini pola khas gRPC error handling.
            System.out.println("[CLIENT] RPC gagal: " + e.getStatus().getCode() +
                    " -- " + e.getStatus().getDescription());
        }
    }

    private static void watchProgress(TransferServiceGrpc.TransferServiceBlockingStub stub,
                                       String source, String destination, long amount, String refId) {
        TransferRequest request = TransferRequest.newBuilder()
                .setSourceAccount(source)
                .setDestinationAccount(destination)
                .setAmount(amount)
                .setReferenceId(refId)
                .build();

        System.out.println("[CLIENT] Mulai watch progress untuk: " + refId);

        // Blocking stub untuk server streaming mengembalikan Iterator --
        // client "menarik" (pull) tiap item saat siap, walau di jaringan
        // server yang "mendorong" (push) tiap item begitu tersedia.
        Iterator<TransferStatusUpdate> updates = stub.watchTransferProgress(request);
        while (updates.hasNext()) {
            TransferStatusUpdate update = updates.next();
            System.out.println("[CLIENT] Update diterima: " + update.getStage() +
                    " (" + update.getProgressPercent() + "%)");
        }
        System.out.println("[CLIENT] Stream selesai (server memanggil onCompleted)");
    }
}
