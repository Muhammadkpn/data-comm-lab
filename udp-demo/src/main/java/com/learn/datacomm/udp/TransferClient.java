package com.learn.datacomm.udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Tiga skenario terhadap UdpTransferServer (jalankan TransferServer dulu):
 *
 *   A. Fire-and-forget  : kirim 10 transfer sekaligus, tanpa retransmisi
 *                         -> sebagian HILANG, ack datang ACAK urutannya
 *   B. Stop-and-wait    : kirim 10 transfer satu per satu dengan timeout + retransmisi
 *                         -> semua sampai, server mendeteksi DUPLIKAT
 *   C. Datagram terlalu besar untuk buffer penerima -> dipotong DIAM-DIAM
 */
public class TransferClient {

    public static void main(String[] args) throws Exception {
        SocketAddress server = TransferDatagram.localhost(TransferServer.PORT);
        String run = String.valueOf(System.currentTimeMillis() % 100_000);

        try (DatagramSocket socket = new DatagramSocket()) {
            System.out.println("=== A. Fire-and-forget (seperti UDP apa adanya) ===");
            for (int seq = 1; seq <= 10; seq++) {
                socket.send(TransferDatagram.request(seq, "1010001", "2020002", 10_000L * seq, "A-" + run + "-" + seq, server));
            }
            System.out.println("[CLIENT] 10 datagram terkirim (send() selalu 'sukses' -- UDP tidak tahu apakah sampai)");

            List<Integer> arrival = new ArrayList<>();
            socket.setSoTimeout(1_000);
            byte[] buf = new byte[256];
            while (true) {
                DatagramPacket ack = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(ack);
                } catch (SocketTimeoutException e) {
                    break;
                }
                arrival.add(Integer.parseInt(TransferDatagram.text(ack).split("\\|")[1]));
            }
            TreeSet<Integer> lost = new TreeSet<>();
            for (int seq = 1; seq <= 10; seq++) {
                if (!arrival.contains(seq)) {
                    lost.add(seq);
                }
            }
            System.out.println("[CLIENT] Urutan ack yang diterima : " + arrival);
            System.out.println("[CLIENT] Tidak ada kabar (hilang)  : " + lost +
                    "  -- client tidak tahu: request yang hilang, atau ack-nya?");

            System.out.println("\n=== B. Stop-and-wait + retransmisi (keandalan dibangun sendiri) ===");
            ReliableSender sender = new ReliableSender(socket, server, 300, 6);
            int ok = 0;
            for (int seq = 1; seq <= 10; seq++) {
                String status = sender.send(100 + seq, "1010001", "2020002", 10_000L * seq, "B-" + run + "-" + seq);
                System.out.println("[CLIENT] seq=" + (100 + seq) + " -> " + (status == null ? "GAGAL setelah semua percobaan" : status));
                if (status != null) {
                    ok++;
                }
            }
            System.out.println("[CLIENT] Berhasil " + ok + "/10 dengan " + sender.retransmissions() + " retransmisi" +
                    " -- lihat log server untuk DUPLIKAT yang di-dedupe");

            System.out.println("\n=== C. Datagram lebih besar dari buffer penerima ===");
            StringBuilder big = new StringBuilder("TRF|999|1010001|2020002|1|C-" + run + "|");
            while (big.length() < 2_000) {
                big.append("catatan-panjang-");
            }
            socket.send(TransferDatagram.packet(big.toString(), server));
            System.out.println("[CLIENT] Terkirim 1 datagram " + big.length() + " byte; server membaca dengan buffer 1024 byte.");
            System.out.println("[CLIENT] Lihat log server: sisa byte DIBUANG tanpa error. (Di TCP, sisa byte menunggu read() berikutnya.)");
        }
    }
}
