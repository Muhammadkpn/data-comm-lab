package com.learn.datacomm.ofs;

/**
 * ==== FORMAT NATIVE OFS ====
 *
 * OFS (Oracle FLEXCUBE messaging format yang umum dipakai di core banking)
 * versi "native" adalah teks terstruktur dengan delimiter, BUKAN JSON dan
 * BUKAN XML. Strukturnya berupa header pipe-delimited diikuti body yang
 * dibungkus kurung kurawal `{ }`, dengan field di dalamnya dipisah koma.
 *
 * Contoh bentuk yang disederhanakan untuk pembelajaran ini:
 *
 *   HEADER,FUNCTION,SOURCE{sourceAccount,destinationAccount,amount,referenceId}
 *
 * Dibanding ISO 8583 (biner, posisi-berbasis-bitmap) dan REST/JSON
 * (self-describing lewat nama field), native OFS ada di tengah: TEKS, tapi
 * strukturnya tetap POSISIONAL (urutan field di dalam `{}` sudah baku
 * sesuai definisi function id-nya) -- bukan key-value seperti JSON.
 *
 * Ini yang membuat native OFS ringkas untuk dibaca mesin (parsing sederhana
 * split-by-delimiter) tapi TIDAK self-describing -- kalau tidak tahu urutan
 * field yang benar untuk function tertentu, pesan ini tidak bisa diartikan
 * dengan benar hanya dari melihat teksnya saja (beda dengan XML/JSON yang
 * nama field-nya eksplisit ada di pesan).
 */
public class NativeFormatCodec {

    private static final String FUNCTION_ID = "FTXFRQ"; // contoh function id transfer dana ala FLEXCUBE

    public static String encodeRequest(TransferMessage msg) {
        return "HEADER,IN," + FUNCTION_ID + "{" +
                msg.getSourceAccount() + "," +
                msg.getDestinationAccount() + "," +
                msg.getAmount() + "," +
                msg.getReferenceId() +
                "}";
    }

    public static TransferMessage decodeRequest(String raw) {
        String body = extractBody(raw);
        String[] fields = body.split(",");
        return new TransferMessage(fields[0], fields[1], Long.parseLong(fields[2]), fields[3]);
    }

    public static String encodeResponse(String referenceId, String status, String message) {
        return "HEADER,OUT," + FUNCTION_ID + "{" +
                referenceId + "," + status + "," + message +
                "}";
    }

    /** Mengembalikan array [referenceId, status, message]. */
    public static String[] decodeResponse(String raw) {
        String body = extractBody(raw);
        return body.split(",", 3);
    }

    /** Ambil isi di dalam kurung kurawal { }, bagian yang sesungguhnya berisi data field. */
    private static String extractBody(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return raw.substring(start + 1, end);
    }
}
