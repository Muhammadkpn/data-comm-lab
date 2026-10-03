package com.learn.datacomm.iso8583;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;

import java.io.IOException;
import java.io.InputStream;

/**
 * Helper untuk membangun ISOMsg dari data transfer kita, dan sebaliknya.
 * Memisahkan "bagaimana cara membentuk pesan ISO 8583" dari logic
 * client/server supaya lebih gampang dibaca.
 */
public class TransferMessageBuilder {

    public static GenericPackager loadPackager() throws ISOException, IOException {
        try (InputStream is = TransferMessageBuilder.class.getClassLoader()
                .getResourceAsStream("transfer-packager.xml")) {
            return new GenericPackager(is);
        }
    }

    /**
     * Bentuk pesan REQUEST transfer dana.
     *
     * MTI (Message Type Indicator) "0200" dibaca per-digit:
     *   0 - versi ISO 8583:1987
     *   2 - kelas pesan: Financial Message
     *   0 - fungsi: Request
     *   0 - originator: Acquirer
     * Ini murni konvensi ISO 8583 -- MTI menyatakan JENIS pesan tanpa perlu
     * field terpisah seperti "messageType" di JSON.
     */
    public static ISOMsg buildTransferRequest(GenericPackager packager, String sourceAccount,
                                                String destinationAccount, long amount, String referenceId)
            throws ISOException {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        msg.setMTI("0200");

        msg.set(2, sourceAccount);
        msg.set(3, "400000"); // Processing Code: 40 = transfer, 00/00 = default account types
        msg.set(4, String.format("%012d", amount)); // DE 4 fixed 12 digit, amount dalam minor unit
        msg.set(11, referenceId); // dipakai sebagai STAN untuk demo ini (di dunia nyata STAN != referenceId bebas)
        msg.set(37, referenceId);
        msg.set(41, "TERM0001");
        msg.set(102, destinationAccount);

        return msg;
    }

    /**
     * Bentuk pesan RESPONSE. MTI "0210" = Financial Message, fungsi Response.
     * Perhatikan: MTI response = MTI request + 10 (konvensi standar ISO 8583
     * untuk memasangkan request dan response secara implisit lewat angka).
     */
    public static ISOMsg buildTransferResponse(ISOMsg request, String responseCode, String responseMessage)
            throws ISOException {
        ISOMsg response = (ISOMsg) request.clone();
        response.setResponseMTI(); // otomatis ubah fungsi MTI dari Request (0) ke Response (1) -> "0210"
        response.set(39, responseCode); // "00" = approved, selain itu = ditolak

        // DE 39 hanya 2 digit, jadi pesan penolakan dikirim terpisah via
        // console log di sisi server -- bukan bagian dari struktur ISO 8583
        // standar yang punya "message" bebas seperti REST.
        System.out.println("[SERVER] (info tambahan, bukan bagian pesan ISO 8583) " + responseMessage);

        return response;
    }
}
