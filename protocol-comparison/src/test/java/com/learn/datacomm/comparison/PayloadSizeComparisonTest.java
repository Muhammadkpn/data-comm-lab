package com.learn.datacomm.comparison;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PayloadSizeComparisonTest {

    @Test
    void urutanUkuranSesuaiKarakterFormat() throws Exception {
        List<PayloadSizeComparison.Row> rows = PayloadSizeComparison.measure();
        assertEquals(6, rows.size());
        assertEquals("Protobuf", rows.get(0).getFormat(), "biner + nomor field = paling kecil");
        assertEquals("OFS XML", rows.get(rows.size() - 1).getFormat(), "tag buka-tutup per field = paling besar");

        int json = size(rows, "JSON");
        int protobuf = size(rows, "Protobuf");
        assertTrue(json > 2 * protobuf, "JSON membawa nama field di setiap pesan");
    }

    private static int size(List<PayloadSizeComparison.Row> rows, String format) {
        return rows.stream().filter(r -> r.getFormat().equals(format)).findFirst().get().getBytes();
    }
}
