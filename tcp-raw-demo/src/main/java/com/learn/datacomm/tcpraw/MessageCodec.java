package com.learn.datacomm.tcpraw;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * ==== INI BAGIAN YANG PALING SPESIFIK KE "RAW TCP" ====
 *
 * TCP adalah stream of bytes, BUKAN stream of messages. Tidak ada konsep
 * "satu send() = satu receive()" seperti UDP, dan tidak ada delimiter pesan
 * bawaan seperti HTTP (yang punya Content-Length header atau chunked encoding).
 *
 * Kalau server mengirim dua pesan berturut-turut, penerima bisa saja membaca
 * keduanya sekaligus dalam satu read(), atau satu pesan bisa "pecah" jadi
 * beberapa kali read() -- tergantung kondisi jaringan/buffer OS. Ini yang
 * disebut "TCP tidak kenal batas pesan".
 *
 * Solusinya: kita definisikan framing sendiri. Di sini dipakai skema paling
 * umum -- LENGTH-PREFIXED FRAMING:
 *
 *   [ 4 byte panjang payload (int, big-endian) ][ N byte payload ]
 *
 * Penerima WAJIB baca 4 byte dulu untuk tahu berapa byte payload yang harus
 * dibaca selanjutnya. Kalau langkah ini salah (misal salah hitung panjang,
 * atau endian-nya kebalik), penerima akan salah interpretasi batas pesan --
 * inilah yang didemokan di {@link BadFramingDemo}.
 *
 * Ini persis yang "hilang" kalau kita tidak pakai HTTP: di HTTP, framing
 * (Content-Length / chunked) sudah diurus oleh library HTTP-nya. Di raw TCP,
 * KITA yang harus implementasi sendiri.
 */
public class MessageCodec {

    private static final String FIELD_SEP = "|";

    /**
     * Encode TransferMessage jadi payload teks sederhana, lalu bungkus dengan
     * 4-byte length prefix. Format payload: pipe-delimited (bukan poin utama
     * pembelajaran ini, jadi sengaja dibuat sesederhana mungkin).
     *
     * Contoh hasil untuk pesan di TransferClient:
     *
     *   payload : "1010001|2020002|150000|REF-TCP-0001"   -> 35 byte UTF-8
     *   frame   : [00 00 00 23][1010001|2020002|150000|REF-TCP-0001]
     *              \_________/  \_________________________________/
     *               4 byte        35 byte
     *               0x23 = 35     payload
     *
     * dos.writeInt() SELALU menulis tepat 4 byte big-endian (byte paling
     * signifikan lebih dulu), berapa pun nilainya -- 35 ditulis sebagai
     * 00 00 00 23, bukan cuma 23. Sifat "selalu 4 byte" inilah yang membuat
     * penerima tahu persis harus membaca berapa byte sebelum tahu isi
     * pesannya, dan kenapa readInt() di sisi seberang selalu cocok.
     *
     * flush() di akhir memastikan byte benar-benar didorong ke socket, tidak
     * mengendap di buffer -- kalau tidak, penerima bisa menunggu data yang
     * sebenarnya belum pernah dikirim.
     */
    public static void writeMessage(OutputStream out, TransferMessage msg) throws IOException {
        String payload = String.join(FIELD_SEP,
                msg.getSourceAccount(),
                msg.getDestinationAccount(),
                String.valueOf(msg.getAmount()),
                msg.getReferenceId());
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(payloadBytes.length); // <-- length prefix, jantungnya framing
        dos.write(payloadBytes);
        dos.flush();
    }

    /**
     * Baca 1 frame lengkap: baca 4 byte length dulu, baru baca sejumlah itu
     * byte payload.
     *
     * URUTAN INI TIDAK BOLEH DIBALIK dan tidak boleh "ditebak". Panjang payload
     * hanya bisa diketahui dari 4 byte prefix -- tidak ada cara lain, karena
     * payload-nya sendiri tidak punya penanda akhir (tidak ada newline, tidak
     * ada null terminator). Begitu posisi baca meleset satu byte saja, semua
     * pembacaan berikutnya ikut geser. Contoh akibatnya ada di
     * {@link BadFramingDemo}.
     *
     * Kenapa readFully(), bukan read()?
     *
     * `read(buffer)` hanya menjamin mengembalikan >= 1 byte, TIDAK menjamin
     * mengisi buffer sampai penuh. Untuk payload 35 byte, satu panggilan
     * read() bisa saja cuma mengembalikan 20 byte -- sisanya masih di
     * perjalanan. Kalau hasil read() langsung dianggap satu pesan utuh, pesan
     * jadi terpotong padahal datanya sebenarnya tidak hilang, cuma belum tiba.
     *
     * readFully() menutup celah itu: dia memanggil read() berulang kali di
     * balik layar sampai buffer benar-benar penuh, atau melempar
     * EOFException kalau koneksi keburu putus.
     */
    public static TransferMessage readMessage(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        int length = dis.readInt();

        // Penjagaan (sanity check) terhadap framing yang sudah geser.
        //
        // Kalau posisi baca meleset, 4 byte yang terbaca sebagai "length"
        // sebenarnya adalah potongan teks payload. Empat karakter ASCII yang
        // ditafsirkan sebagai int 32-bit menghasilkan angka yang sangat besar
        // (ratusan juta), karena tiap karakter menempati satu byte penuh.
        //
        // Tanpa penjagaan ini, `new byte[length]` akan mencoba mengalokasikan
        // array raksasa -- program mati dengan OutOfMemoryError yang sama
        // sekali tidak menjelaskan akar masalahnya. Dengan penjagaan ini,
        // pesan error-nya langsung menunjuk ke penyebab yang benar: framing.
        if (length < 0 || length > 10_000) {
            throw new IOException("Panjang payload tidak masuk akal: " + length +
                    " -- kemungkinan besar framing salah baca posisi byte");
        }

        byte[] payloadBytes = new byte[length];
        dis.readFully(payloadBytes); // baca PERSIS `length` byte, walau perlu beberapa kali read() di socket

        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        String[] fields = payload.split("\\" + FIELD_SEP);
        return new TransferMessage(fields[0], fields[1], Long.parseLong(fields[2]), fields[3]);
    }

    /** Encode/decode untuk response, formatnya jauh lebih sederhana: "STATUS|message". */
    public static void writeResponse(OutputStream out, String status, String message) throws IOException {
        String payload = status + FIELD_SEP + message;
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(payloadBytes.length);
        dos.write(payloadBytes);
        dos.flush();
    }

    public static String[] readResponse(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        int length = dis.readInt();
        byte[] payloadBytes = new byte[length];
        dis.readFully(payloadBytes);
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        return payload.split("\\" + FIELD_SEP, 2);
    }
}
