package com.learn.datacomm.ofs;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * OFS (baik native maupun XML) secara historis ditransport lewat MQ Series
 * (message queue) di kebanyakan instalasi FLEXCUBE produksi -- tapi
 * mekanisme framing pesannya sendiri tidak berubah walau ditransport lewat
 * TCP langsung (disederhanakan untuk demo ini, skip MQ).
 *
 * Beda dengan tcp-raw-demo (length-prefix biner) dan iso8583-demo
 * (length-prefix 2-byte), OFS di sini pakai DELIMITER-BASED FRAMING: pesan
 * diakhiri satu karakter kontrol khusus, ETX (End of Text, 0x03), yang
 * tidak mungkin muncul di tengah payload teks biasa.
 *
 * Trade-off delimiter-based vs length-prefixed:
 *   - Delimiter-based: penerima harus SCAN byte demi byte mencari delimiter
 *     -- sedikit lebih lambat untuk pesan besar, tapi gampang di-debug
 *     karena bisa dibaca langsung sebagai teks di network capture.
 *   - Length-prefixed (seperti tcp-raw-demo/iso8583-demo): penerima langsung
 *     tahu berapa byte yang harus dibaca, TAPI kalau payload teks kebetulan
 *     mengandung delimiter, itu tidak masalah (tidak perlu escaping) --
 *     beda dengan delimiter-based yang perlu memastikan delimiter tidak
 *     pernah muncul di payload asli.
 */
public class OfsTransport {

    private static final int DELIMITER = 0x03; // ETX, dipilih karena tidak lazim muncul di teks OFS biasa

    public static void writeMessage(OutputStream out, String payload) throws IOException {
        out.write(payload.getBytes(StandardCharsets.UTF_8));
        out.write(DELIMITER);
        out.flush();
    }

    public static String readMessage(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == DELIMITER) {
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
            buffer.write(b);
        }
        throw new EOFException("Stream berakhir sebelum delimiter ditemukan -- pesan tidak lengkap");
    }
}
