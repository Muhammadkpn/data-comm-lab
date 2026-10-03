package com.learn.datacomm.tcpraw;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * ==== DEMO KHUSUS: apa yang terjadi kalau framing salah dibaca ====
 *
 * Ini contoh negatif yang SENGAJA dibuat untuk menunjukkan karakteristik
 * TCP-as-byte-stream. Server di sini mengirim DUA pesan berturut-turut
 * langsung tanpa jeda (back-to-back writes). Karena TCP bisa
 * menggabungkan/memecah data secara bebas di level OS, kedua pesan itu
 * bisa saja tiba di client dalam satu "gumpalan" bytes.
 *
 * Client "buggy" di sini membaca dengan asumsi SALAH: dia mengira 4 byte
 * pertama dari gumpalan itu adalah panjang pesan yang valid, padahal bisa
 * jadi itu adalah potongan byte dari tengah payload pesan pertama (kalau
 * pembacaan sebelumnya sudah offset). Untuk membuat kesalahan ini
 * KONSISTEN dan gampang diamati, kita simulasikan client yang mengabaikan
 * length prefix sama sekali dan langsung menebak-nebak batas pesan pakai
 * ukuran tetap yang salah.
 *
 * Yang mau ditunjukkan: TANPA length-prefix framing yang benar, pembacaan
 * bisa "geser" dan sisa data di stream jadi sampah yang tidak bisa
 * di-parse -- ini yang disebut "desync" antara pengirim dan penerima.
 * Server/HTTP framework biasa menangani ini secara otomatis; di raw TCP,
 * ini murni tanggung jawab kita.
 */
public class BadFramingDemo {

    private static final int PORT = 9091;

    public static void main(String[] args) throws Exception {
        Thread serverThread = new Thread(BadFramingDemo::runServer);
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(300); // beri waktu server siap listen
        runBuggyClient();
    }

    /** Server mengirim 2 pesan back-to-back tanpa jeda -- memicu kemungkinan digabung di level TCP. */
    private static void runServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[BAD-FRAMING SERVER] listening on port " + PORT);
            Socket socket = serverSocket.accept();
            OutputStream out = socket.getOutputStream();

            TransferMessage msg1 = new TransferMessage("1010001", "2020002", 150_000L, "REF-A");
            TransferMessage msg2 = new TransferMessage("3030003", "4040004", 75_000L, "REF-B");

            System.out.println("[BAD-FRAMING SERVER] Mengirim frame 1: " + msg1);
            MessageCodec.writeMessage(out, msg1);

            System.out.println("[BAD-FRAMING SERVER] Mengirim frame 2 langsung tanpa jeda: " + msg2);
            MessageCodec.writeMessage(out, msg2);

        } catch (IOException e) {
            System.out.println("[BAD-FRAMING SERVER] error: " + e.getMessage());
        }
    }

    /**
     * Client "buggy": alih-alih membaca 4-byte length prefix dengan benar,
     * dia salah asumsi bahwa SETIAP pesan selalu berukuran tepat 20 byte
     * payload (padahal ukuran payload sebenarnya bervariasi tergantung
     * panjang field). Ini mensimulasikan bug framing yang umum terjadi:
     * hard-coding ukuran alih-alih membaca length prefix yang sesungguhnya.
     */
    private static void runBuggyClient() throws IOException {
        try (Socket socket = new Socket("localhost", PORT)) {
            InputStream in = socket.getInputStream();

            System.out.println("\n[BUGGY CLIENT] Membaca dengan asumsi SALAH: skip 4 byte length, " +
                    "lalu selalu baca tepat 20 byte sebagai payload...");

            DataInputStream dis = new DataInputStream(in);

            for (int frameNo = 1; frameNo <= 2; frameNo++) {
                // ==== DI SINILAH LETAK BUG-NYA (dua baris di bawah) ====
                //
                // Baris 1: readInt() dipanggil -- jadi 4 byte prefix memang
                // ikut terbaca dan posisi stream maju dengan benar -- TAPI
                // nilai kembaliannya dibuang begitu saja. Padahal justru angka
                // itulah satu-satunya sumber informasi berapa byte payload
                // yang harus dibaca.
                //
                // Baris 2: ukuran payload di-hard-code 20 byte. Ini menebak,
                // bukan membaca. Selama panjang payload sebenarnya != 20,
                // posisi baca pasti meleset.
                //
                // Efek melesetnya menumpuk, bukan berdiri sendiri: sisa byte
                // frame pertama yang tidak terbaca akan tertinggal di stream,
                // lalu ikut terbaca sebagai bagian dari frame berikutnya.
                // Inilah yang disebut desync -- pengirim dan penerima tidak
                // lagi sepakat di mana satu pesan berakhir.
                //
                // Bandingkan dengan MessageCodec.readMessage() yang memakai
                // nilai readInt() itu untuk menentukan ukuran buffer.
                dis.readInt(); // length prefix DIBACA tapi SENGAJA DIABAIKAN -- inilah bug-nya

                byte[] wrongSizedPayload = new byte[20]; // ukuran ditebak, bukan dibaca dari prefix

                // read() (bukan readFully()) dipakai di sini supaya demo tidak
                // menggantung saat byte yang tersedia lebih sedikit dari 20.
                int bytesRead = dis.read(wrongSizedPayload);

                String garbled = new String(wrongSizedPayload, 0, Math.max(bytesRead, 0), StandardCharsets.UTF_8);
                System.out.println("[BUGGY CLIENT] Frame " + frameNo + " (SALAH BACA): \"" + garbled + "\"");
            }

            System.out.println("\n[BUGGY CLIENT] Perhatikan: frame 1 kemungkinan terpotong/tidak lengkap,");
            System.out.println("[BUGGY CLIENT] dan frame 2 kemungkinan sudah 'nyasar' membaca sisa byte");
            System.out.println("[BUGGY CLIENT] dari frame 1 -- inilah desync akibat framing yang salah.");
            System.out.println("[BUGGY CLIENT] Bandingkan dengan TransferClient yang membaca length prefix");
            System.out.println("[BUGGY CLIENT] dengan benar dan selalu mendapat batas pesan yang tepat.");

        } catch (EOFException e) {
            System.out.println("[BUGGY CLIENT] Stream habis di tengah pembacaan -- ini juga akibat " +
                    "salah hitung berapa byte yang seharusnya dibaca.");
        }
    }
}
