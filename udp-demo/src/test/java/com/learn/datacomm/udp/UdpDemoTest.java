package com.learn.datacomm.udp;

import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class UdpDemoTest {

    @Test
    void tanpaLossSemuaAckKembali() throws Exception {
        try (UdpTransferServer server = new UdpTransferServer(0, 0.0, 1);
             DatagramSocket socket = new DatagramSocket()) {
            server.start();
            ReliableSender sender = new ReliableSender(socket, TransferDatagram.localhost(server.port()), 500, 1);
            assertEquals("SUCCESS", sender.send(1, "A", "B", 10, "REF-1"));
            assertEquals(0, sender.retransmissions());
        }
    }

    @Test
    void retransmisiMembuatSemuaSampaiDanServerMendedupe() throws Exception {
        try (UdpTransferServer server = new UdpTransferServer(0, 0.3, 42);
             DatagramSocket socket = new DatagramSocket()) {
            server.start();
            ReliableSender sender = new ReliableSender(socket, TransferDatagram.localhost(server.port()), 250, 10);
            for (int seq = 1; seq <= 20; seq++) {
                assertEquals("SUCCESS", sender.send(seq, "A", "B", seq, "REF-" + seq), "seq " + seq);
            }
            assertTrue(sender.retransmissions() > 0, "dengan loss 30% pasti ada retransmisi");
            assertEquals(20, server.processedCount(), "setiap transfer diproses TEPAT sekali");
            assertTrue(server.duplicateCount() > 0, "ack yang hilang memicu duplikat yang harus di-dedupe");
        }
    }

    @Test
    void satuSendSatuReceiveTanpaFraming() throws Exception {
        try (DatagramSocket receiver = new DatagramSocket(0);
             DatagramSocket sender = new DatagramSocket()) {
            SocketAddress to = TransferDatagram.localhost(receiver.getLocalPort());
            sender.send(TransferDatagram.packet("pesan-satu", to));
            sender.send(TransferDatagram.packet("dua", to));

            receiver.setSoTimeout(1_000);
            byte[] buf = new byte[1024];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            receiver.receive(p);
            assertEquals("pesan-satu", TransferDatagram.text(p));
            p = new DatagramPacket(buf, buf.length);
            receiver.receive(p);
            assertEquals("dua", TransferDatagram.text(p), "batas pesan terjaga walau dikirim back-to-back");
        }
    }

    @Test
    void datagramMelebihiBufferDipotongDiamDiam() throws Exception {
        try (DatagramSocket receiver = new DatagramSocket(0);
             DatagramSocket sender = new DatagramSocket()) {
            StringBuilder big = new StringBuilder();
            while (big.length() < 2_000) {
                big.append("0123456789");
            }
            sender.send(TransferDatagram.packet(big.toString(), TransferDatagram.localhost(receiver.getLocalPort())));

            receiver.setSoTimeout(1_000);
            DatagramPacket p = new DatagramPacket(new byte[1024], 1024);
            receiver.receive(p);
            assertEquals(1024, p.getLength());

            // Sisa 976 byte TIDAK menunggu di socket -- sudah dibuang.
            assertThrows(SocketTimeoutException.class, () -> receiver.receive(new DatagramPacket(new byte[1024], 1024)));
        }
    }
}
