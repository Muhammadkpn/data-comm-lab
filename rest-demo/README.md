# rest-demo

Belajar **REST API** (Spring Boot) dengan skenario yang sama seperti modul lain: transfer
dana antar rekening. Fokus: request-response standar, status code yang tepat, dan validasi.

## Cara run

Butuh 2 terminal.

**Terminal 1 — jalankan server:**
```bash
mvn -pl rest-demo spring-boot:run
```
Server jalan di `http://localhost:8080`.

**Terminal 2 — jalankan client:**
```bash
mvn -pl rest-demo compile exec:java -Dexec.mainClass="com.learn.datacomm.rest.client.TransferClient"
```

Client akan menjalankan 5 skenario berturut-turut: happy path, business rule rejection
(422), validation error (400), GET yang ketemu (200), dan GET yang tidak ketemu (404).

### Coba manual pakai curl

```bash
curl -i -X POST http://localhost:8080/transfers \
  -H "Content-Type: application/json" \
  -d '{"sourceAccount":"1010001","destinationAccount":"2020002","amount":150000,"referenceId":"REF-CURL-01"}'
```

## Alur komunikasi

```
CLIENT                                          SERVER
  |                                                |
  |--- POST /transfers                             |
  |    Content-Type: application/json ------------>|
  |    { sourceAccount, destinationAccount, ... }   |
  |                                                 |  Spring:
  |                                                 |  - deserialize JSON -> TransferRequest
  |                                                 |  - jalankan validasi (@Valid)
  |                                                 |  - route ke method controller yang tepat
  |                                                 |
  |<--- 201 Created                                 |
  |     { referenceId, status, message } -----------|
```

Status code yang dipakai di modul ini:
- **201 Created** — transfer berhasil diproses (resource baru "tercipta")
- **400 Bad Request** — request tidak lolos validasi format (field kosong / amount <= 0),
  ditangani otomatis oleh Spring lewat `@Valid`
- **422 Unprocessable Entity** — request valid secara format, tapi ditolak business rule
  (source account == destination account)
- **200 OK** — GET berhasil menemukan data
- **404 Not Found** — GET dengan referenceId yang tidak ada

## Yang perlu diperhatikan di kode

- **`TransferController`** — di sinilah semua hal spesifik-REST kelihatan: routing lewat
  `@PostMapping`/`@GetMapping`, pemetaan hasil ke status code yang berbeda-beda, dan
  perbedaan antara error format (400, auto) vs error business rule (422, manual).
- **`TransferRequest`** — validasi field pakai `@NotBlank`/`@Positive` dari
  `javax.validation`, dicek otomatis saat controller method dianotasi `@Valid`.
- Dibandingkan `tcp-raw-demo`: tidak ada framing manual, tidak ada parsing string manual —
  semua diurus Spring + Jackson. Ini yang dimaksud "apa yang hilang" kalau pakai HTTP.
- Tidak ada persistence sungguhan (in-memory check sederhana saja) — fokus ke mekanisme
  protokolnya, bukan business logic yang realistis.

## Ketika kontrak berubah

Ini pembanding langsung untuk `grpc-demo/schema-break-demo.sh`. Pertanyaannya: kalau
bentuk data yang dikirim client bergeser sementara server belum tahu apa-apa, kapan
kesalahannya ketahuan?

**Jalankan `RestDemoApplication` dulu**, lalu di terminal kedua:

```bash
mvn -pl rest-demo compile exec:java \
    -Dexec.mainClass="com.learn.datacomm.rest.client.SchemaDriftClient"
```

`SchemaDriftClient` mengirim **JSON mentah** (String, bukan DTO) ke `TransferController`
yang tidak diubah sama sekali — supaya drift-nya benar-benar sepihak, seperti kejadian
nyata saat hanya salah satu sisi yang di-deploy ulang.

Hasil yang sudah diverifikasi:

| # | Perubahan di JSON | Hasil |
|---|---|---|
| 1 | `referenceId` → `traceId` (rename) | **400** — `referenceId` jadi null, ditolak `@NotBlank` |
| 2 | `destinationAccount` dihapus | **400** — ditolak `@NotBlank` |
| 3 | Tambah `currency` & `channel` | **201** — field asing **diabaikan diam-diam** |
| 4 | `amount: "seratus ribu"` | **400** — Jackson gagal konversi ke `long` |
| 5 | `amount` dihapus | **400** — jadi `0`, ditolak `@Positive` |
| 6 | `amount: 150000.99` | **201** — **dipotong diam-diam jadi `150000`** |

**Semua skenario di atas COMPILE tanpa satu pun error.** Itulah inti perbedaannya: bagi
compiler Java, JSON hanyalah `String`. Tiga perubahan pertama yang di gRPC langsung
menghentikan build, di sini baru ketahuan setelah request benar-benar dikirim.

Dua catatan supaya perbandingannya tidak berat sebelah:

- **Skenario 1 & 2 tertangkap karena ada `@NotBlank`, bukan karena REST-nya lebih aman.**
  Tanpa anotasi validasi itu, field yang hilang akan lolos sebagai `null` tanpa keluhan.
  Validasi di REST adalah sesuatu yang harus ditulis dan dijaga manual; di gRPC sebagian
  dijamin tipe data.
- **Skenario 3 & 6 adalah kegagalan diam-diam yang sesungguhnya.** Keduanya dijawab
  **201 SUCCESS** padahal datanya tidak utuh. Cek log server untuk skenario 6: yang
  tercatat `amount=150000`, sen-nya hilang tanpa peringatan. Ini kembaran persis dari
  perilaku protobuf saat tipe field berubah (lihat `SchemaDriftDemo` skenario 3 di
  `grpc-demo`) — sama-sama lolos semua pemeriksaan, sama-sama diam-diam salah.

Kesimpulannya: REST lebih **fleksibel** (client dan server boleh beda versi tanpa rebuild
serentak), dan itu keunggulan nyata di sistem besar. Harganya, semua pemeriksaan digeser
ke runtime — dan status `2xx` bukan jaminan datanya benar. Pembahasan lengkap dua arah
ada di `grpc-demo/README.md` bagian "Ketika kontrak berubah".
