package com.learn.datacomm.ofs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FormatCodecTest {

    private final TransferMessage msg = new TransferMessage("ACC-001", "ACC-002", 250_000, "REF-OFS-1");

    @Test
    void nativeRequestRoundTrip() {
        String raw = NativeFormatCodec.encodeRequest(msg);
        assertEquals("HEADER,IN,FTXFRQ{ACC-001,ACC-002,250000,REF-OFS-1}", raw);

        TransferMessage decoded = NativeFormatCodec.decodeRequest(raw);
        assertEquals(msg.toString(), decoded.toString());
    }

    @Test
    void nativeResponseMessageBolehMengandungKoma() {
        String raw = NativeFormatCodec.encodeResponse("REF-OFS-1", "SUCCESS", "ok, diproses");
        assertArrayEquals(new String[]{"REF-OFS-1", "SUCCESS", "ok, diproses"},
                NativeFormatCodec.decodeResponse(raw));
    }

    @Test
    void xmlRequestRoundTrip() throws Exception {
        TransferMessage decoded = XmlFormatCodec.decodeRequest(XmlFormatCodec.encodeRequest(msg));
        assertEquals(msg.toString(), decoded.toString());
    }

    @Test
    void xmlResponseRoundTrip() throws Exception {
        String xml = XmlFormatCodec.encodeResponse("REF-OFS-1", "REJECTED", "Saldo kurang");
        assertArrayEquals(new String[]{"REF-OFS-1", "REJECTED", "Saldo kurang"},
                XmlFormatCodec.decodeResponse(xml));
    }

    @Test
    void xmlLebihBesarDariNativeUntukDataYangSama() {
        // Trade-off self-describing: XML membawa nama field di setiap pesan.
        assertTrue(XmlFormatCodec.encodeRequest(msg).length() > 3 * NativeFormatCodec.encodeRequest(msg).length());
    }
}
