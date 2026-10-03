package com.learn.datacomm.udp;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Format pesan di atas UDP. Bandingkan dengan MessageCodec di tcp-raw-demo:
 *
 *   TCP: byte stream tanpa batas -> WAJIB length prefix supaya penerima tahu pesan
 *        berakhir di mana.
 *   UDP: satu send() = satu datagram = satu receive(). Batas pesan DIJAGA oleh
 *        protokol, jadi tidak perlu framing. Sebagai gantinya, tidak ada jaminan
 *        datagram SAMPAI, tidak ada jaminan URUTAN, dan bisa saja datang DUA KALI.
 *
 *   request  "TRF|<seq>|<source>|<destination>|<amount>|<referenceId>"
 *   ack      "ACK|<seq>|<status>"
 *
 * `seq` dibuat client supaya bisa mencocokkan ack dengan request (karena urutan acak)
 * dan supaya server bisa mendeteksi duplikat.
 */
public final class TransferDatagram {

    private TransferDatagram() {
    }

    public static DatagramPacket request(int seq, String src, String dst, long amount, String ref, SocketAddress to) {
        return packet("TRF|" + seq + "|" + src + "|" + dst + "|" + amount + "|" + ref, to);
    }

    public static DatagramPacket ack(int seq, String status, SocketAddress to) {
        return packet("ACK|" + seq + "|" + status, to);
    }

    public static DatagramPacket packet(String text, SocketAddress to) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new DatagramPacket(bytes, bytes.length, to);
    }

    /** Hanya byte sepanjang getLength() yang valid -- sisa buffer adalah sampah dari receive sebelumnya. */
    public static String text(DatagramPacket packet) {
        return new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
    }

    public static SocketAddress localhost(int port) {
        return new InetSocketAddress("127.0.0.1", port);
    }
}
