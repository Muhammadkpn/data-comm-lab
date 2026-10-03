package com.learn.datacomm.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pakai transport in-process: server & client gRPC sungguhan (stub hasil generate,
 * serialisasi protobuf, Status code) tapi tanpa membuka port TCP.
 */
class TransferServiceImplTest {

    private Server server;
    private ManagedChannel channel;
    private TransferServiceGrpc.TransferServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new TransferServiceImpl()).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = TransferServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    private static TransferRequest request(String src, String dst) {
        return TransferRequest.newBuilder()
                .setSourceAccount(src).setDestinationAccount(dst)
                .setAmount(100_000).setReferenceId("REF-GRPC-T").build();
    }

    @Test
    void unarySukses() {
        TransferResponse response = stub.transfer(request("ACC-001", "ACC-002"));
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("REF-GRPC-T", response.getReferenceId());
    }

    @Test
    void unaryRekeningSamaMenjadiInvalidArgument() {
        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> stub.transfer(request("ACC-001", "ACC-001")));
        assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
    }

    @Test
    void serverStreamingMengirimSemuaTahapBerurutan() {
        Iterator<TransferStatusUpdate> it = stub.watchTransferProgress(request("ACC-001", "ACC-002"));
        List<String> stages = new ArrayList<>();
        it.forEachRemaining(u -> stages.add(u.getStage()));

        assertEquals(java.util.Arrays.asList("VALIDATING", "DEBIT_SOURCE", "CREDIT_DESTINATION", "COMPLETED"), stages);
    }
}
