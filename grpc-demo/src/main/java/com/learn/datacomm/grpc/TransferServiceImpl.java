package com.learn.datacomm.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * Implementasi service, di-generate skeleton-nya dari transfer.proto lewat
 * TransferServiceGrpc.TransferServiceImplBase. Kita cukup override method
 * RPC yang didefinisikan di .proto -- signature-nya sudah type-safe
 * (generated dari schema, bukan string/JSON bebas seperti REST/raw TCP).
 */
public class TransferServiceImpl extends TransferServiceGrpc.TransferServiceImplBase {

    /**
     * ==== UNARY RPC ====
     * Satu TransferRequest masuk, satu TransferResponse keluar lewat
     * responseObserver.onNext() + onCompleted(). Pola ini paling mirip
     * request-response REST biasa.
     */
    @Override
    public void transfer(TransferRequest request, StreamObserver<TransferResponse> responseObserver) {
        System.out.println("[SERVER] [Unary] Menerima: " + request.getSourceAccount() +
                " -> " + request.getDestinationAccount() + ", amount=" + request.getAmount());

        // ==== Error handling ala gRPC: io.grpc.Status ====
        // Bukan exception biasa dan bukan status code HTTP -- gRPC punya set
        // status code sendiri (INVALID_ARGUMENT, NOT_FOUND, INTERNAL, dst)
        // yang dikirim lewat trailer HTTP/2, dipisah dari "body" response.
        // Client menerima ini sebagai StatusRuntimeException yang bisa
        // diperiksa kodenya secara terprogram.
        if (request.getSourceAccount().equals(request.getDestinationAccount())) {
            System.out.println("[SERVER] [Unary] Ditolak: source == destination -> INVALID_ARGUMENT");
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Source dan destination account tidak boleh sama")
                            .asRuntimeException());
            return;
        }

        TransferResponse response = TransferResponse.newBuilder()
                .setReferenceId(request.getReferenceId())
                .setStatus("SUCCESS")
                .setMessage("Transfer berhasil diproses")
                .build();

        System.out.println("[SERVER] [Unary] Mengirim response SUCCESS");
        responseObserver.onNext(response);
        responseObserver.onCompleted(); // wajib dipanggil untuk menandai RPC selesai
    }

    /**
     * ==== SERVER STREAMING RPC ====
     * Satu request masuk, tapi responseObserver.onNext() dipanggil BERKALI-KALI
     * sebelum akhirnya onCompleted(). Semua ini terjadi dalam satu koneksi
     * HTTP/2 yang sama -- client menerima tiap update secara real-time tanpa
     * perlu request ulang (beda dengan REST yang butuh polling berulang).
     */
    @Override
    public void watchTransferProgress(TransferRequest request, StreamObserver<TransferStatusUpdate> responseObserver) {
        System.out.println("[SERVER] [Streaming] Mulai memproses transfer " + request.getReferenceId());

        String[] stages = {"VALIDATING", "DEBIT_SOURCE", "CREDIT_DESTINATION", "COMPLETED"};
        int[] progress = {10, 40, 75, 100};

        for (int i = 0; i < stages.length; i++) {
            TransferStatusUpdate update = TransferStatusUpdate.newBuilder()
                    .setReferenceId(request.getReferenceId())
                    .setStage(stages[i])
                    .setProgressPercent(progress[i])
                    .build();

            System.out.println("[SERVER] [Streaming] Kirim update: " + stages[i] + " (" + progress[i] + "%)");
            responseObserver.onNext(update); // kirim satu item stream, TANPA menutup RPC

            try {
                Thread.sleep(400); // simulasi waktu pemrosesan tiap tahap
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("[SERVER] [Streaming] Semua tahap selesai, menutup stream");
        responseObserver.onCompleted(); // baru di sini stream benar-benar selesai
    }
}
