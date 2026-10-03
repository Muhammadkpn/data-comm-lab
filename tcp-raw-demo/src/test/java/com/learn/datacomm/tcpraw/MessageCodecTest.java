package com.learn.datacomm.tcpraw;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class MessageCodecTest {

    @Test
    void roundTripSatuPesan() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageCodec.writeMessage(out, new TransferMessage("ACC-001", "ACC-002", 150_000, "REF-1"));

        TransferMessage decoded = MessageCodec.readMessage(new ByteArrayInputStream(out.toByteArray()));

        assertEquals("ACC-001", decoded.getSourceAccount());
        assertEquals("ACC-002", decoded.getDestinationAccount());
        assertEquals(150_000, decoded.getAmount());
        assertEquals("REF-1", decoded.getReferenceId());
    }

    @Test
    void duaPesanBerurutanTetapTerpisahKarenaLengthPrefix() throws IOException {
        // Inti framing: dua pesan ditulis back-to-back ke stream yang sama,
        // reader tetap bisa memisahkan karena tiap pesan diawali panjangnya.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageCodec.writeMessage(out, new TransferMessage("A", "B", 1, "REF-A"));
        MessageCodec.writeMessage(out, new TransferMessage("C", "D", 2, "REF-B"));

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        assertEquals("REF-A", MessageCodec.readMessage(in).getReferenceId());
        assertEquals("REF-B", MessageCodec.readMessage(in).getReferenceId());
    }

    @Test
    void bytesDatangSedikitDemiSedikitTetapTerbacaUtuh() throws IOException {
        // Simulasi TCP yang mengirim data terpecah: read() hanya mengembalikan 1 byte
        // per panggilan. readFully() di codec harus tetap menyusun pesan secara utuh.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageCodec.writeMessage(out, new TransferMessage("ACC-001", "ACC-002", 99, "REF-SLOW"));
        InputStream trickle = new OneByteAtATimeInputStream(out.toByteArray());

        assertEquals("REF-SLOW", MessageCodec.readMessage(trickle).getReferenceId());
    }

    @Test
    void lengthPrefixTidakMasukAkalDitolak() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new DataOutputStream(out).writeInt(1_000_000);

        IOException ex = assertThrows(IOException.class,
                () -> MessageCodec.readMessage(new ByteArrayInputStream(out.toByteArray())));
        assertTrue(ex.getMessage().contains("tidak masuk akal"));
    }

    @Test
    void responseRoundTripMempertahankanSeparatorDiMessage() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageCodec.writeResponse(out, "SUCCESS", "ok|dengan pipe");

        String[] parts = MessageCodec.readResponse(new ByteArrayInputStream(out.toByteArray()));
        assertArrayEquals(new String[]{"SUCCESS", "ok|dengan pipe"}, parts);
    }

    private static final class OneByteAtATimeInputStream extends ByteArrayInputStream {
        OneByteAtATimeInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            return super.read(b, off, Math.min(len, 1));
        }
    }
}
