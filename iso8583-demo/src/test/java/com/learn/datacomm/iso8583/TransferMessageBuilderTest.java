package com.learn.datacomm.iso8583;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransferMessageBuilderTest {

    private static GenericPackager packager;

    @BeforeAll
    static void loadPackager() throws Exception {
        packager = TransferMessageBuilder.loadPackager();
    }

    @Test
    void requestPackUnpackRoundTrip() throws Exception {
        ISOMsg request = TransferMessageBuilder.buildTransferRequest(
                packager, "1234567890", "9876543210", 150_000, "000001");

        byte[] packed = request.pack();

        ISOMsg unpacked = new ISOMsg();
        unpacked.setPackager(packager);
        unpacked.unpack(packed);

        assertEquals("0200", unpacked.getMTI());
        assertEquals("1234567890", unpacked.getString(2));
        assertEquals("400000", unpacked.getString(3));
        assertEquals("000000150000", unpacked.getString(4));
        assertEquals("9876543210", unpacked.getString(102));
    }

    @Test
    void responseMengubahMtiDanMenambahDe39() throws Exception {
        ISOMsg request = TransferMessageBuilder.buildTransferRequest(
                packager, "1234567890", "9876543210", 150_000, "000001");

        ISOMsg response = TransferMessageBuilder.buildTransferResponse(request, "00", "approved");

        assertEquals("0210", response.getMTI());
        assertEquals("00", response.getString(39));
        assertEquals("0200", request.getMTI(), "request asli tidak boleh ikut berubah (clone)");
    }
}
