package com.learn.datacomm.grpc;

import com.google.protobuf.InvalidProtocolBufferException;
import com.learn.datacomm.grpc.drift.v1.TransferRequest;
import com.learn.datacomm.grpc.drift.v2.ReuseNumberSafe;

import java.util.Arrays;

/**
 * ==== KEGAGALAN gRPC YANG **TIDAK** DITANGKAP COMPILER ====
 *
 * schema-break-demo.sh menunjukkan sisi terang gRPC: kalau proto berubah dan
 * kode Java-mu belum menyesuaikan, `mvn compile` GAGAL. Error pindah dari
 * runtime ke compile time.
 *
 * File ini menunjukkan sisi gelapnya, yang jauh lebih jarang dibahas:
 * compile time hanya menjaga keselarasan **kode <-> skema di satu modul**.
 * Ia TIDAK bisa menjaga keselarasan **versi skema antar dua proses**.
 *
 * Skenarionya: client masih memakai proto v1, server sudah di-deploy dengan
 * proto v2. Dua-duanya compile bersih (masing-masing hanya tahu versinya
 * sendiri), tapi byte yang dikirim client ditafsirkan dengan aturan berbeda
 * oleh server.
 *
 * Kuncinya ada di satu fakta wire format protobuf:
 *
 *     NAMA FIELD TIDAK PERNAH DIKIRIM. Yang dikirim hanya NOMOR FIELD + tipe
 *     wire + nilainya.
 *
 * Akibatnya sering berlawanan dengan intuisi:
 *   - Rename field  -> AMAN (nama tidak ada di wire)
 *   - Ganti tipe    -> TIDAK melempar exception, tapi diam-diam jadi kosong
 *   - Hapus field   -> datanya masih ikut terkirim lagi diam-diam
 *
 * Demo ini murni serialize/parse di memori, jadi TIDAK perlu server jalan.
 * Jalankan dengan:
 *   mvn -pl grpc-demo compile exec:java \
 *       -Dexec.mainClass="com.learn.datacomm.grpc.SchemaDriftDemo"
 */
public class SchemaDriftDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("Client memakai proto v1, server memakai proto v2.");
        System.out.println("Keduanya compile BERSIH -- tidak ada error sama sekali.\n");

        // Inilah pesan yang dikirim client lama. Dari sisi client, semuanya benar.
        TransferRequest v1 = TransferRequest.newBuilder()
                .setSourceAccount("1010001")
                .setDestinationAccount("2020002")
                .setAmount(150_000L)
                .setReferenceId("REF-GRPC-0001")
                .build();

        byte[] wire = v1.toByteArray();

        System.out.println("[CLIENT v1] Mengirim: source=" + v1.getSourceAccount() +
                ", destination=" + v1.getDestinationAccount() +
                ", amount=" + v1.getAmount() +
                ", referenceId=" + v1.getReferenceId());
        System.out.println("[CLIENT v1] Ukuran payload: " + wire.length + " byte");

        // Server mem-parse byte yang sama pakai definisi v2. Tidak ada exception.
        com.learn.datacomm.grpc.drift.v2.TransferRequest v2 =
                com.learn.datacomm.grpc.drift.v2.TransferRequest.parseFrom(wire);

        System.out.println("[SERVER v2] parseFrom() BERHASIL -- tidak ada exception.\n");

        skenario1Rename(v2);
        skenario2HapusField(v2, wire);
        skenario3GantiTipeWireBeda(v1, v2);
        skenario4GantiTipeWireSama();
        skenario5FieldBaru(v2);
        skenario6ByteKorup(wire);
        skenario7NomorDipakaiUlang();

        ringkasan();
    }

    /**
     * Rename field, nomor tetap sama -> nilainya tetap sampai dengan BENAR.
     * Ini membuktikan nama field tidak ikut dikirim di wire.
     */
    private static void skenario1Rename(com.learn.datacomm.grpc.drift.v2.TransferRequest v2) {
        System.out.println("=== 1. Field di-RENAME (reference_id -> trace_id, nomor tetap 4) ===");
        System.out.println("[SERVER v2] trace_id = \"" + v2.getTraceId() + "\"");
        System.out.println("-> AMAN. Nilai tetap utuh walau namanya beda di kedua sisi,");
        System.out.println("   karena yang dicocokkan adalah NOMOR field (4), bukan namanya.\n");
    }

    /**
     * Field dihapus di v2 -> hilang dari API, TAPI byte-nya tidak dibuang.
     * Protobuf menyimpannya di "unknown fields" dan ikut mengirimnya lagi saat
     * pesan itu di-serialize ulang.
     */
    private static void skenario2HapusField(com.learn.datacomm.grpc.drift.v2.TransferRequest v2,
                                            byte[] wireAsli) {
        System.out.println("=== 2. Field DIHAPUS (destination_account = 2) ===");
        System.out.println("[SERVER v2] Tidak ada lagi getDestinationAccount() -- field hilang dari API.");
        System.out.println("[SERVER v2] Nomor field yang tidak dikenali: " +
                v2.getUnknownFields().asMap().keySet());

        byte[] ulang = v2.toByteArray();
        System.out.println("[SERVER v2] Ukuran saat di-serialize ulang: " + ulang.length +
                " byte (asli " + wireAsli.length + " byte)");
        System.out.println("-> BAHAYA HALUS. Nomor rekening tujuan TIDAK terbaca oleh server,");
        System.out.println("   tapi byte-nya tetap menempel dan ikut TERKIRIM LAGI ke hilir.");
        System.out.println("   Service perantara bisa meneruskan data yang tak bisa ia lihat.\n");
    }

    /**
     * Ganti tipe dengan wire type berbeda (int64 varint -> string
     * length-delimited). Yang terjadi BUKAN exception, melainkan nilai default
     * (string kosong) -- nilai aslinya "diparkir" jadi unknown field.
     */
    private static void skenario3GantiTipeWireBeda(TransferRequest v1,
                                                   com.learn.datacomm.grpc.drift.v2.TransferRequest v2) {
        System.out.println("=== 3. Tipe DIGANTI, wire type BEDA (int64 amount -> string amount) ===");
        System.out.println("[CLIENT v1] amount dikirim sebagai angka : " + v1.getAmount());
        System.out.println("[SERVER v2] amount terbaca sebagai string: \"" + v2.getAmount() + "\"" +
                " (kosong, panjang=" + v2.getAmount().length() + ")");
        System.out.println("-> PALING BERBAHAYA. TIDAK ADA EXCEPTION sama sekali.");
        System.out.println("   Nominal transfer Rp150.000 diam-diam jadi string kosong.");
        System.out.println("   Kalau server menganggap kosong = 0, transfer diproses dengan nilai 0");
        System.out.println("   dan tidak ada satu pun log error yang muncul.\n");
    }

    /**
     * Ganti tipe TAPI wire type-nya sama (int32 dan int64 sama-sama varint).
     * Ini justru perubahan yang aman -- penting ditunjukkan supaya kesimpulannya
     * tidak jadi "semua perubahan tipe itu bahaya".
     */
    private static void skenario4GantiTipeWireSama() throws InvalidProtocolBufferException {
        System.out.println("=== 4. Tipe DIGANTI, wire type SAMA (int32 small -> int64 small) ===");

        com.learn.datacomm.grpc.drift.v1.WidenNumber kecil =
                com.learn.datacomm.grpc.drift.v1.WidenNumber.newBuilder().setSmall(42).build();
        com.learn.datacomm.grpc.drift.v2.WidenNumber lebar =
                com.learn.datacomm.grpc.drift.v2.WidenNumber.parseFrom(kecil.toByteArray());

        System.out.println("[CLIENT v1] small (int32) = " + kecil.getSmall());
        System.out.println("[SERVER v2] small (int64) = " + lebar.getSmall());
        System.out.println("-> AMAN. int32 dan int64 sama-sama diencode sebagai varint,");
        System.out.println("   jadi nilainya terbaca benar. Tidak semua ganti tipe itu merusak --");
        System.out.println("   yang menentukan adalah WIRE TYPE-nya berubah atau tidak.\n");
    }

    /** Field baru di v2 yang tidak pernah dikirim client v1 -> jadi default value. */
    private static void skenario5FieldBaru(com.learn.datacomm.grpc.drift.v2.TransferRequest v2) {
        System.out.println("=== 5. Field BARU ditambah di v2 (currency = 5) ===");
        System.out.println("[SERVER v2] currency = \"" + v2.getCurrency() + "\" (string kosong)");
        System.out.println("-> AMAN TAPI PERLU SADAR. Client lama tidak pernah mengirim field ini,");
        System.out.println("   jadi server menerima default value -- bukan error, bukan null.");
        System.out.println("   Di proto3 tidak ada beda antara \"tidak dikirim\" dan \"dikirim kosong\".\n");
    }

    /**
     * Satu-satunya kondisi yang benar-benar melempar exception: byte-nya rusak.
     * Bukan ketidakcocokan skema.
     */
    private static void skenario6ByteKorup(byte[] wire) {
        System.out.println("=== 6. Byte KORUP / terpotong di tengah jalan ===");

        byte[] terpotong = Arrays.copyOf(wire, wire.length - 3);
        try {
            com.learn.datacomm.grpc.drift.v2.TransferRequest.parseFrom(terpotong);
            System.out.println("[SERVER v2] Parse berhasil (tidak diharapkan).");
        } catch (InvalidProtocolBufferException e) {
            System.out.println("[SERVER v2] " + e.getClass().getSimpleName() + " dilempar.");
            System.out.println("[SERVER v2] Pesan: " + e.getMessage());
        }
        System.out.println("-> Perhatikan: INI SATU-SATUNYA skenario yang melempar exception.");
        System.out.println("   Jadi \"tidak ada error\" pada skenario 1-5 TIDAK berarti datanya benar.\n");
    }

    /**
     * Menghapus field lalu memakai ulang nomornya = cara paling ampuh merusak
     * data secara diam-diam, karena wire type-nya cocok sehingga protobuf tidak
     * menganggapnya unknown field.
     */
    private static void skenario7NomorDipakaiUlang() throws InvalidProtocolBufferException {
        System.out.println("=== 7. Nomor field DIPAKAI ULANG setelah dihapus (nomor 2) ===");

        com.learn.datacomm.grpc.drift.v1.ReuseNumber lama =
                com.learn.datacomm.grpc.drift.v1.ReuseNumber.newBuilder()
                        .setSourceAccount("1010001")
                        .setDestinationAccount("2020002")
                        .build();

        com.learn.datacomm.grpc.drift.v2.ReuseNumber baru =
                com.learn.datacomm.grpc.drift.v2.ReuseNumber.parseFrom(lama.toByteArray());

        System.out.println("[CLIENT v1] field 2 = destination_account = \"" +
                lama.getDestinationAccount() + "\"");
        System.out.println("[SERVER v2] field 2 = bank_name          = \"" +
                baru.getBankName() + "\"");
        System.out.println("-> KORUPSI DATA DIAM-DIAM. Nomor rekening tujuan terbaca sebagai");
        System.out.println("   NAMA BANK, karena nomornya sama (2) dan wire type-nya sama (string).");
        System.out.println("   Tidak masuk unknown fields, tidak ada exception -- hanya salah arti.");

        ReuseNumberSafe aman = ReuseNumberSafe.newBuilder()
                .setSourceAccount("1010001")
                .setBankName("BANK-XYZ")
                .build();
        System.out.println("[PENCEGAHAN] Pakai `reserved 2;` seperti di ReuseNumberSafe:");
        System.out.println("             protoc akan MENOLAK compile kalau nomor 2 dipakai lagi,");
        System.out.println("             sehingga kesalahan ini balik lagi jadi error compile time.");
        System.out.println("             Contoh pesan aman: source=" + aman.getSourceAccount() +
                ", bank=" + aman.getBankName() + " (field 3, bukan 2)\n");
    }

    private static void ringkasan() {
        System.out.println("=== RINGKASAN: kapan gRPC menangkap kesalahan? ===");
        System.out.println();
        System.out.println("  Jenis perubahan                     | Compile | Runtime | Akibat");
        System.out.println("  ------------------------------------+---------+---------+-------------------------");
        System.out.println("  Rename field (nomor tetap)          | GAGAL*  | -       | aman, nilai utuh");
        System.out.println("  Hapus field                         | GAGAL*  | senyap  | ikut terkirim lagi");
        System.out.println("  Ganti tipe, wire type beda          | GAGAL*  | SENYAP  | jadi kosong/0 -- BAHAYA");
        System.out.println("  Ganti tipe, wire type sama          | GAGAL*  | -       | aman, nilai utuh");
        System.out.println("  Tambah field                        | sukses  | -       | default value");
        System.out.println("  Pakai ulang nomor field             | sukses  | SENYAP  | salah arti -- BAHAYA");
        System.out.println("  Byte korup                          | -       | ERROR   | exception (terdeteksi)");
        System.out.println();
        System.out.println("  * GAGAL saat compile HANYA kalau kode Java di modul yang sama ikut");
        System.out.println("    dibangun ulang terhadap proto baru (itu yang schema-break-demo.sh");
        System.out.println("    tunjukkan). Kalau client lama TIDAK di-rebuild -- persis situasi");
        System.out.println("    demo ini -- compiler tidak pernah tahu, dan kolomnya jadi 'senyap'.");
        System.out.println();
        System.out.println("Kesimpulan: gRPC memindahkan error kode<->skema ke compile time,");
        System.out.println("tapi ketidakselarasan versi<->versi antar proses tetap di runtime,");
        System.out.println("dan bentuknya sering DIAM-DIAM SALAH, bukan gagal dengan error.");
        System.out.println("Karena itu aturan `reserved` dan larangan memakai ulang nomor field");
        System.out.println("bukan formalitas -- itu satu-satunya pengaman yang tersisa.");
    }
}
