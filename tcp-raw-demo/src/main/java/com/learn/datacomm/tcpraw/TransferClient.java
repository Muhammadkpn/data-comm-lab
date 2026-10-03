package com.learn.datacomm.tcpraw;

import java.io.IOException;
import java.net.Socket;

/**
 * Client TCP sederhana: buka koneksi baru ke server, kirim satu
 * TransferMessage sebagai satu frame, tunggu satu frame response, lalu
 * tutup koneksi.
 *
 * Perhatikan: TIDAK ada "request line", TIDAK ada header seperti
 * Content-Type, TIDAK ada status code HTTP. Client dan server harus SUDAH
 * SEPAKAT DI LUAR PROTOKOL (lewat dokumentasi/kontrak kode) soal urutan
 * field dan format framing. Kalau salah satu pihak salah asumsi, tidak ada
 * mekanisme protokol yang otomatis memberi tahu -- beda dengan HTTP yang
 * punya status code standar (400, 500, dst) untuk menandai kesalahan.
 */
public class TransferClient {

    private static final String HOST = "localhost";
    private static final int PORT = 9090;

    public static void main(String[] args) throws IOException {
        TransferMessage request = new TransferMessage(
                "1010001", "2020002", 150_000L, "REF-TCP-0001");

        System.out.println("[CLIENT] Mengirim: " + request);

        try (Socket socket = new Socket(HOST, PORT)) {
            // Urutan tulis-lalu-baca di bawah ini adalah KONTRAK yang disepakati
            // manual dengan TransferServer.handleClient(), bukan aturan yang
            // dipaksakan TCP. TCP sendiri full-duplex: kedua pihak boleh menulis
            // kapan saja, bahkan bersamaan. Yang menentukan "siapa bicara duluan"
            // murni kode kita.
            //
            // Kalau urutan ini dibalik (client membaca duluan), client akan
            // menunggu response yang tidak akan pernah datang -- karena server
            // juga sedang menunggu request. Tidak ada timeout bawaan, tidak ada
            // pesan error: prosesnya hanya diam menggantung.

            // Kirim request sebagai satu frame (length-prefixed)
            MessageCodec.writeMessage(socket.getOutputStream(), request);

            // Tunggu dan baca response sebagai satu frame juga
            String[] response = MessageCodec.readResponse(socket.getInputStream());
            System.out.println("[CLIENT] Response status : " + response[0]);
            System.out.println("[CLIENT] Response message: " + response[1]);
        }
    }
}
