package com.learn.datacomm.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server UDP yang SEKALIGUS mensimulasikan jaringan yang tidak andal, supaya efeknya
 * terlihat di localhost (yang di dunia nyata hampir tidak pernah kehilangan paket):
 *
 *   - request hilang  : datagram masuk dibuang dengan peluang `lossRate`
 *   - ack hilang      : ack keluar dibuang dengan peluang `lossRate`
 *   - reorder         : setiap ack ditunda acak 0..150 ms, jadi urutan ack teracak
 *
 * Random memakai seed tetap supaya hasil demo bisa diulang persis sama.
 *
 * Tidak ada "koneksi" di UDP: tidak ada accept(), tidak ada handshake, tidak ada
 * state per client yang dikelola OS. Satu socket menerima datagram dari siapa saja.
 */
public class UdpTransferServer implements AutoCloseable {

    private final DatagramSocket socket;
    private final double lossRate;
    private final Random random;
    private final ScheduledExecutorService delayer = Executors.newSingleThreadScheduledExecutor();
    /** referenceId yang sudah diproses -- dedupe karena retransmisi client bisa datang dua kali. */
    private final Set<String> processed = ConcurrentHashMap.newKeySet();
    private final AtomicInteger processedCount = new AtomicInteger();
    private final AtomicInteger duplicateCount = new AtomicInteger();

    public UdpTransferServer(int port, double lossRate, long seed) throws SocketException {
        this.socket = new DatagramSocket(port);
        this.lossRate = lossRate;
        this.random = new Random(seed);
    }

    public int port() {
        return socket.getLocalPort();
    }

    public void start() {
        Thread t = new Thread(this::loop, "udp-server");
        t.setDaemon(true);
        t.start();
    }

    private void loop() {
        byte[] buffer = new byte[1024];
        while (!socket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                return; // socket ditutup
            }
            handle(packet);
        }
    }

    private void handle(DatagramPacket packet) {
        String text = TransferDatagram.text(packet);
        String[] f = text.split("\\|");
        if (f.length != 6 || !"TRF".equals(f[0])) {
            System.out.println("[SERVER] Datagram tidak dikenal (" + packet.getLength() + " byte), diabaikan");
            return;
        }
        int seq = Integer.parseInt(f[1]);
        String ref = f[5];

        if (random.nextDouble() < lossRate) {
            System.out.println("[SERVER] (simulasi) request seq=" + seq + " HILANG di jaringan");
            return;
        }

        String status;
        if (processed.add(ref)) {
            processedCount.incrementAndGet();
            status = "SUCCESS";
            System.out.println("[SERVER] seq=" + seq + " diproses: " + f[2] + " -> " + f[3] + " amount=" + f[4]);
        } else {
            // Ack sebelumnya hilang, client mengirim ulang. JANGAN debit lagi -- cukup ulangi ack.
            duplicateCount.incrementAndGet();
            status = "SUCCESS";
            System.out.println("[SERVER] seq=" + seq + " DUPLIKAT (" + ref + " sudah diproses) -> kirim ulang ack saja");
        }

        boolean dropAck = random.nextDouble() < lossRate;
        long delay = random.nextInt(150);
        delayer.schedule(() -> {
            if (dropAck) {
                System.out.println("[SERVER] (simulasi) ack seq=" + seq + " HILANG di jaringan");
                return;
            }
            try {
                socket.send(TransferDatagram.ack(seq, status, packet.getSocketAddress()));
            } catch (IOException ignored) {
                // socket sudah ditutup
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    public int processedCount() {
        return processedCount.get();
    }

    public int duplicateCount() {
        return duplicateCount.get();
    }

    @Override
    public void close() {
        delayer.shutdownNow();
        socket.close();
    }
}
