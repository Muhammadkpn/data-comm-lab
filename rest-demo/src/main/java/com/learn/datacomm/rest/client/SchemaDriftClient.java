package com.learn.datacomm.rest.client;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * ==== PEMBANDING REST UNTUK grpc-demo/schema-break-demo.sh ====
 *
 * Client ini mengirim JSON MENTAH (String, bukan DTO) ke TransferController
 * yang TIDAK diubah sama sekali. Tujuannya meniru situasi nyata: client sudah
 * mengubah bentuk data, server belum tahu apa-apa.
 *
 * Poin utamanya: SELURUH skenario di bawah ini COMPILE DENGAN MULUS. Tidak ada
 * satu pun yang ditolak sebelum aplikasi jalan -- karena bagi compiler Java,
 * JSON hanyalah String biasa. Bandingkan dengan gRPC, di mana perubahan serupa
 * langsung menghentikan build (jalankan grpc-demo/schema-break-demo.sh).
 *
 * Inilah sisi "fleksibel" REST sekaligus harganya: fleksibel = tidak ada yang
 * memeriksa, jadi kesalahan baru muncul saat request sungguhan dikirim -- dan
 * sebagian TIDAK muncul sama sekali (skenario 3 dan 5).
 *
 * Jalankan RestDemoApplication dulu, lalu:
 *   mvn -pl rest-demo compile exec:java \
 *       -Dexec.mainClass="com.learn.datacomm.rest.client.SchemaDriftClient"
 */
public class SchemaDriftClient {

    private static final String BASE_URL = "http://localhost:8080/transfers";

    public static void main(String[] args) {
        RestTemplate restTemplate = new RestTemplate();

        System.out.println("Server (TransferController) TIDAK diubah sama sekali.");
        System.out.println("Client mengirim JSON yang bentuknya sudah bergeser.");
        System.out.println("Semua skenario di bawah ini COMPILE tanpa error.\n");

        System.out.println("=== 1. Field di-RENAME: referenceId -> traceId ===");
        System.out.println("Di gRPC ini BUILD FAILURE. Di sini? compile mulus, kirim saja.");
        kirim(restTemplate,
                "{\"sourceAccount\":\"1010001\",\"destinationAccount\":\"2020002\"," +
                        "\"amount\":150000,\"traceId\":\"REF-REST-9001\"}");
        System.out.println("-> Jackson mengabaikan 'traceId' (tidak dikenal) dan 'referenceId'");
        System.out.println("   jadi null, lalu @NotBlank menolaknya -> 400.");
        System.out.println("   CATATAN JUJUR: yang menyelamatkan di sini adalah anotasi @NotBlank,");
        System.out.println("   BUKAN REST-nya. Tanpa validasi itu, null akan lolos diam-diam.\n");

        System.out.println("=== 2. Field DIHAPUS: destinationAccount tidak dikirim ===");
        kirim(restTemplate,
                "{\"sourceAccount\":\"1010001\",\"amount\":150000," +
                        "\"referenceId\":\"REF-REST-9002\"}");
        System.out.println("-> Sama seperti di atas: jadi null lalu ditolak @NotBlank -> 400.\n");

        System.out.println("=== 3. Field DITAMBAH: currency & channel yang server tak kenal ===");
        kirim(restTemplate,
                "{\"sourceAccount\":\"1010001\",\"destinationAccount\":\"2020002\"," +
                        "\"amount\":150000,\"referenceId\":\"REF-REST-9003\"," +
                        "\"currency\":\"IDR\",\"channel\":\"MOBILE\"}");
        System.out.println("-> 201 Created. Field asing DIABAIKAN DIAM-DIAM tanpa peringatan.");
        System.out.println("   Ini padanan persis 'unknown fields' di protobuf: data terkirim,");
        System.out.println("   server tidak melihatnya, dan tidak ada yang memberi tahu siapa pun.\n");

        System.out.println("=== 4. Tipe data SALAH: amount dikirim sebagai teks ===");
        kirim(restTemplate,
                "{\"sourceAccount\":\"1010001\",\"destinationAccount\":\"2020002\"," +
                        "\"amount\":\"seratus ribu\",\"referenceId\":\"REF-REST-9004\"}");
        System.out.println("-> 400. Jackson gagal mengubah teks jadi long.");
        System.out.println("   Ketahuan, TAPI baru saat request sampai di server -- di gRPC,");
        System.out.println("   kesalahan setara sudah menghentikan build sejak awal.\n");

        System.out.println("=== 5. Field DIHAPUS: amount tidak dikirim ===");
        kirim(restTemplate,
                "{\"sourceAccount\":\"1010001\",\"destinationAccount\":\"2020002\"," +
                        "\"referenceId\":\"REF-REST-9005\"}");
        System.out.println("-> 400, tapi perhatikan ALASANNYA: 'amount' bertipe primitif long,");
        System.out.println("   jadi field yang hilang diam-diam menjadi 0 -- dan yang menolaknya");
        System.out.println("   adalah @Positive (0 tidak positif), bukan karena field-nya hilang.");
        System.out.println("   Tanpa @Positive, transfer Rp0 akan LOLOS sebagai 201.\n");

        System.out.println("=== 6. KORUPSI DIAM-DIAM: amount 150000.99 (ada desimal) ===");
        kirim(restTemplate,
                "{\"sourceAccount\":\"1010001\",\"destinationAccount\":\"2020002\"," +
                        "\"amount\":150000.99,\"referenceId\":\"REF-REST-9006\"}");
        System.out.println("-> 201 SUCCESS, TAPI cek log server: amount yang tercatat = 150000.");
        System.out.println("   Jackson memotong ,99 diam-diam supaya muat ke long. Tidak ada");
        System.out.println("   error, tidak ada peringatan, uangnya hilang begitu saja.");
        System.out.println("   INILAH kembaran REST dari skenario 3 di SchemaDriftDemo (gRPC):");
        System.out.println("   sama-sama lolos semua pemeriksaan, sama-sama diam-diam salah.\n");

        ringkasan();
    }

    private static void kirim(RestTemplate restTemplate, String jsonMentah) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        System.out.println("[CLIENT] POST body: " + jsonMentah);
        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(BASE_URL, new HttpEntity<>(jsonMentah, headers), String.class);
            System.out.println("[CLIENT] " + response.getStatusCodeValue() + " -- body: " + response.getBody());
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            if (body.length() > 220) {
                body = body.substring(0, 220) + "...";
            }
            System.out.println("[CLIENT] " + e.getStatusCode().value() + " -- body: " + body);
        }
    }

    private static void ringkasan() {
        System.out.println("=== RINGKASAN: REST vs gRPC untuk perubahan kontrak yang sama ===");
        System.out.println();
        System.out.println("  Perubahan            | gRPC (grpc-demo)  | REST (di sini)");
        System.out.println("  ---------------------+-------------------+---------------------------");
        System.out.println("  Rename field         | BUILD FAILURE     | compile OK -> 400 runtime");
        System.out.println("  Hapus field          | BUILD FAILURE     | compile OK -> 400 runtime");
        System.out.println("  Ganti tipe (teks)    | BUILD FAILURE     | compile OK -> 400 runtime");
        System.out.println("  Tambah field         | BUILD SUCCESS     | compile OK -> 201, diabaikan");
        System.out.println("  Desimal ke long      | BUILD FAILURE     | compile OK -> 201, DIPOTONG");
        System.out.println();
        System.out.println("Bacaan yang seimbang:");
        System.out.println();
        System.out.println("  - gRPC lebih cepat memberi tahu: tiga baris pertama ketahuan sebelum");
        System.out.println("    aplikasi dijalankan, lengkap dengan file dan nomor baris.");
        System.out.println("  - REST lebih longgar: client dan server boleh berbeda versi tanpa");
        System.out.println("    harus rebuild bersamaan. Itu keunggulan nyata saat sistemnya besar");
        System.out.println("    dan tidak semua pihak bisa di-deploy serentak.");
        System.out.println("  - Harganya: semua pemeriksaan digeser ke runtime, dan yang 'lolos'");
        System.out.println("    belum tentu benar -- skenario 3 (field asing diabaikan) dan");
        System.out.println("    skenario 6 (desimal dipotong) sama-sama 201 tapi datanya berubah.");
        System.out.println();
        System.out.println("Yang perlu diluruskan: gRPC TIDAK kebal masalah ini. Yang dijaga");
        System.out.println("compiler hanya keselarasan kode <-> skema di modul yang di-build ulang.");
        System.out.println("Kalau dua proses memakai versi proto berbeda, gRPC juga gagal diam-diam");
        System.out.println("-- bahkan lebih senyap dari REST. Buktinya:");
        System.out.println("  mvn -pl grpc-demo compile exec:java \\");
        System.out.println("      -Dexec.mainClass=\"com.learn.datacomm.grpc.SchemaDriftDemo\"");
    }
}
