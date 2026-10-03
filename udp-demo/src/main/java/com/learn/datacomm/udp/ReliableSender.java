package com.learn.datacomm.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

/**
 * Membangun ulang sebagian kecil "keandalan TCP" di atas UDP: STOP-AND-WAIT ARQ.
 *
 *   kirim datagram -> tunggu ACK dengan seq yang sama
 *     - ACK datang         : lanjut ke pesan berikutnya
 *     - timeout            : kirim ULANG (retransmission), maksimal `maxAttempts` kali
 *     - ACK seq lain datang: ack terlambat dari pesan sebelumnya, abaikan
 *
 * Konsekuensi yang harus ditangani SERVER: retransmisi karena ACK hilang membuat
 * request yang sama tiba dua kali -> server wajib dedupe (idempotent), persis seperti
 * Idempotency-Key di rest-demo v2.
 *
 * TCP melakukan semua ini (plus sliding window, congestion control, urutan byte) di
 * kernel. Protokol modern di atas UDP (QUIC/HTTP3, game, VoIP) memilih sendiri bagian
 * keandalan mana yang dibutuhkan.
 */
public class ReliableSender {

    private final DatagramSocket socket;
    private final SocketAddress server;
    private final int timeoutMs;
    private final int maxAttempts;
    private int retransmissions;

    public ReliableSender(DatagramSocket socket, SocketAddress server, int timeoutMs, int maxAttempts) {
        this.socket = socket;
        this.server = server;
        this.timeoutMs = timeoutMs;
        this.maxAttempts = maxAttempts;
    }

    /** @return status dari ACK, atau null kalau tetap gagal setelah semua percobaan. */
    public String send(int seq, String src, String dst, long amount, String ref) throws IOException {
        socket.setSoTimeout(timeoutMs);
        byte[] buf = new byte[256];
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                retransmissions++;
                System.out.println("[CLIENT]   timeout -> kirim ulang seq=" + seq + " (percobaan " + attempt + ")");
            }
            socket.send(TransferDatagram.request(seq, src, dst, amount, ref, server));

            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket ack = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(ack);
                } catch (SocketTimeoutException e) {
                    break;
                }
                String[] f = TransferDatagram.text(ack).split("\\|");
                if (f.length == 3 && "ACK".equals(f[0]) && Integer.parseInt(f[1]) == seq) {
                    return f[2];
                }
                // ack milik seq lama yang datang terlambat -- bukan yang sedang ditunggu
            }
        }
        return null;
    }

    public int retransmissions() {
        return retransmissions;
    }
}
