package com.learn.datacomm.comparison;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.datacomm.grpc.TransferRequest;
import com.learn.datacomm.iso8583.TransferMessageBuilder;
import com.learn.datacomm.ofs.NativeFormatCodec;
import com.learn.datacomm.ofs.XmlFormatCodec;
import com.learn.datacomm.tcpraw.MessageCodec;
import com.learn.datacomm.tcpraw.TransferMessage;
import org.jpos.iso.packager.GenericPackager;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Satu transfer yang SAMA (4 field: source, destination, amount, referenceId) di-encode
 * dengan codec asli setiap modul, lalu dihitung ukuran byte-nya.
 *
 * Yang diukur hanya BODY / pesan aplikasi. Overhead transport (header HTTP, frame HTTP/2,
 * header TCP/IP) dibahas terpisah di docs/comparison.md.
 */
public class PayloadSizeComparison {

    static final String SOURCE = "1234567890";
    static final String DESTINATION = "9876543210";
    static final long AMOUNT = 150_000;
    static final String REFERENCE = "000123";

    public static final class Row {
        final String format;
        final String module;
        final int bytes;
        final boolean selfDescribing;
        final String sample;

        Row(String format, String module, byte[] payload, boolean selfDescribing, boolean binary) {
            this.format = format;
            this.module = module;
            this.bytes = payload.length;
            this.selfDescribing = selfDescribing;
            this.sample = binary ? hex(payload) : new String(payload, StandardCharsets.UTF_8).replace("\n", "");
        }

        public String getFormat() {
            return format;
        }

        public int getBytes() {
            return bytes;
        }
    }

    public static List<Row> measure() throws Exception {
        List<Row> rows = new ArrayList<>();

        // 1. Protobuf (grpc-demo): field diidentifikasi nomor, bukan nama; angka varint.
        byte[] protobuf = TransferRequest.newBuilder()
                .setSourceAccount(SOURCE).setDestinationAccount(DESTINATION)
                .setAmount(AMOUNT).setReferenceId(REFERENCE)
                .build().toByteArray();
        rows.add(new Row("Protobuf", "grpc-demo", protobuf, false, true));

        // 2. Raw TCP frame (tcp-raw-demo): 4 byte length prefix + teks pipe-delimited.
        ByteArrayOutputStream tcp = new ByteArrayOutputStream();
        MessageCodec.writeMessage(tcp, new TransferMessage(SOURCE, DESTINATION, AMOUNT, REFERENCE));
        rows.add(new Row("Raw TCP frame", "tcp-raw-demo", tcp.toByteArray(), false, true));

        // 3. OFS native (ofs-demo): positional, dipisah koma.
        rows.add(new Row("OFS native", "ofs-demo", utf8(NativeFormatCodec.encodeRequest(
                new com.learn.datacomm.ofs.TransferMessage(SOURCE, DESTINATION, AMOUNT, REFERENCE))), false, false));

        // 4. ISO 8583 (iso8583-demo): MTI + bitmap + data element sesuai packager.
        GenericPackager packager = TransferMessageBuilder.loadPackager();
        byte[] iso = TransferMessageBuilder.buildTransferRequest(packager, SOURCE, DESTINATION, AMOUNT, REFERENCE).pack();
        rows.add(new Row("ISO 8583", "iso8583-demo", iso, false, false));

        // 5. JSON (rest-demo / graphql-demo): nama field ikut di setiap pesan.
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("sourceAccount", SOURCE);
        json.put("destinationAccount", DESTINATION);
        json.put("amount", AMOUNT);
        json.put("referenceId", REFERENCE);
        rows.add(new Row("JSON", "rest-demo", new ObjectMapper().writeValueAsBytes(json), true, false));

        // 6. OFS XML (ofs-demo): self-describing, tag pembuka + penutup per field.
        rows.add(new Row("OFS XML", "ofs-demo", utf8(XmlFormatCodec.encodeRequest(
                new com.learn.datacomm.ofs.TransferMessage(SOURCE, DESTINATION, AMOUNT, REFERENCE))), true, false));

        rows.sort((a, b) -> Integer.compare(a.bytes, b.bytes));
        return rows;
    }

    public static void main(String[] args) throws Exception {
        List<Row> rows = measure();
        int smallest = rows.get(0).bytes;
        System.out.println("Transfer: " + SOURCE + " -> " + DESTINATION + ", amount=" + AMOUNT + ", ref=" + REFERENCE + "\n");
        System.out.printf("%-14s %-14s %6s %7s  %-16s %s%n", "Format", "Modul", "Byte", "vs min", "Self-describing", "Isi (hex untuk biner)");
        for (Row r : rows) {
            String sample = r.sample.length() > 70 ? r.sample.substring(0, 67) + "..." : r.sample;
            System.out.printf("%-14s %-14s %6d %6.1fx  %-16s %s%n", r.format, r.module, r.bytes,
                    (double) r.bytes / smallest, r.selfDescribing ? "ya" : "tidak", sample);
        }
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
