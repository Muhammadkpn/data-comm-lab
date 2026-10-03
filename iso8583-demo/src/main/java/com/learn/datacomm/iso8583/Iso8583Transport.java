package com.learn.datacomm.iso8583;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * ISO 8583 sendiri hanya mendefinisikan FORMAT PESAN-nya (MTI, bitmap, data
 * element) -- tidak mendefinisikan cara transportnya di jaringan. Di
 * industri, ISO 8583 paling umum dikirim di atas raw TCP dengan tambahan
 * length header di depan tiap pesan (mirip prinsip framing di tcp-raw-demo,
 * tapi konvensinya sedikit beda: umumnya 2-byte length, bukan 4-byte).
 *
 * Ini menunjukkan bahwa "framing" bukan cuma masalah raw TCP polos --
 * bahkan protokol perbankan yang sudah punya format pesan baku pun tetap
 * butuh mekanisme framing terpisah saat ditransport lewat TCP stream.
 */
public class Iso8583Transport {

    public static void writeMessage(OutputStream out, ISOMsg msg) throws IOException, ISOException {
        byte[] packed = msg.pack();

        DataOutputStream dos = new DataOutputStream(out);
        dos.writeShort(packed.length); // 2-byte length header, konvensi umum ISO 8583 over TCP
        dos.write(packed);
        dos.flush();
    }

    public static ISOMsg readMessage(InputStream in, GenericPackager packager) throws IOException, ISOException {
        DataInputStream dis = new DataInputStream(in);
        int length = dis.readUnsignedShort();

        byte[] packed = new byte[length];
        dis.readFully(packed);

        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        msg.unpack(packed);
        return msg;
    }
}
