package com.learn.datacomm.tcpraw;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Server TCP sederhana: dengar di satu port, terima koneksi, baca 1 frame
 * TransferMessage, "proses" (happy path saja -- selalu sukses), lalu kirim
 * 1 frame response balik.
 *
 * Tidak ada HTTP method, tidak ada URL path, tidak ada header/status code
 * bawaan -- semua "arti" dari byte yang mengalir murni ditentukan oleh
 * kesepakatan aplikasi kita sendiri (lihat MessageCodec). Ini beda mendasar
 * dibanding rest-demo, di mana Spring/HTTP sudah menyediakan struktur itu.
 *
 * Model threading sengaja dibuat paling sederhana: satu thread per koneksi.
 * Cukup untuk demo, bukan untuk skala produksi (tidak ada connection pooling/
 * thread pool tuning -- di luar scope pembelajaran modul ini).
 */
public class TransferServer {

    private static final int PORT = 9090;

    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[SERVER] TCP raw server listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SERVER] Koneksi baru dari " + clientSocket.getRemoteSocketAddress());
                new Thread(() -> handleClient(clientSocket)).start();
            }
        }
    }

    /**
     * Menangani SATU koneksi dari awal sampai tutup. Isinya tiga tahap yang
     * urutannya sudah disepakati diam-diam dengan client:
     *
     *   1. baca 1 frame request   (client harus menulis lebih dulu)
     *   2. proses
     *   3. tulis 1 frame response (client menunggu di readResponse)
     *
     * Kesepakatan itu tidak tertulis di mana pun selain di kode kedua belah
     * pihak. Kalau client dan server sama-sama menunggu giliran membaca,
     * keduanya akan menggantung selamanya tanpa pesan error -- tidak ada
     * mekanisme protokol yang menyelamatkan, beda dengan HTTP yang urutan
     * request-response-nya sudah baku.
     *
     * try-with-resources pada Socket memastikan koneksi ditutup apa pun yang
     * terjadi. Penutupan itu sekaligus menjadi penanda "pesan sudah habis"
     * bagi client -- di raw TCP, EOF adalah satu-satunya sinyal akhir yang
     * datang dari luar aplikasi kita.
     */
    private static void handleClient(Socket socket) {
        try (Socket s = socket) {
            // 1. Baca satu frame lengkap dari stream (lihat MessageCodec untuk detail framing)
            TransferMessage request = MessageCodec.readMessage(s.getInputStream());
            System.out.println("[SERVER] Diterima: " + request);

            // 2. "Proses" transfer -- happy path saja, tidak ada pengecekan saldo dsb.
            System.out.println("[SERVER] Memproses transfer " + request.getAmount() +
                    " dari " + request.getSourceAccount() + " ke " + request.getDestinationAccount() + " ...");

            // 3. Kirim response sebagai frame baru
            MessageCodec.writeResponse(s.getOutputStream(), "SUCCESS",
                    "Transfer " + request.getReferenceId() + " berhasil diproses");
            System.out.println("[SERVER] Response terkirim, koneksi ditutup");

        } catch (IOException e) {
            System.out.println("[SERVER] Gagal menangani koneksi: " + e.getMessage());
        }
    }
}
