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

---

# API v2 — dari "REST yang jalan" ke "REST yang siap production"

Endpoint `/transfers` di atas sengaja dibiarkan apa adanya sebagai **v1**. Semua topik
lanjutan (bab 5–10 materi) ada di bawah prefix `/v2`, yang **stateful** (saldo benar-benar
berubah) supaya efek retry, pagination, dan caching bisa dilihat langsung.

Rekening awal: `1010001` (Andi, 10.000.000, punya 12 mutasi historis), `2020002` (Budi,
5.000.000), `3030003` (Citra, 7.500.000). Restart server = data kembali ke awal.

## Peta endpoint

| Method & path | Topik |
|---|---|
| `POST /v2/transfers` (+ `Idempotency-Key`) | Idempotency, 201 + `Location` |
| `GET /v2/transfers/{id}` | Resource dengan id buatan server (`TRF-0001`) |
| `GET /v2/accounts/{id}` (+ `X-API-Version`, `If-None-Match`) | Header versioning, ETag / 304, `Vary` |
| `GET /v2/accounts/{id}/transactions?page=&size=` | Offset pagination + content negotiation (JSON / CSV / 406) |
| `GET /v2/accounts/{id}/transactions/cursor?cursor=&limit=` | Cursor (keyset) pagination |
| `GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS /v2/beneficiaries[/{id}]` | Semantik method HTTP |
| `POST /anti-pattern/transfers` | Contoh SALAH: `200 OK` + `{success:false}` |
| `/transfers` (v1) | Sekarang membawa header `Deprecation`, `Sunset`, `Link` |

Semua error v2 memakai **`application/problem+json`** (RFC 7807) lewat satu
`@RestControllerAdvice` (`ProblemDetailsHandler`):

```json
{"type":"https://data-comm-lab.local/problems/insufficient-balance","title":"Unprocessable Entity",
 "status":422,"detail":"Saldo rekening 1010001 tidak cukup","instance":"/v2/transfers","traceId":"037c41fa"}
```

`type` stabil dan bisa didokumentasikan (client bercabang berdasarkan ini, bukan berdasarkan
teks `detail`); `traceId` adalah yang disebut user ke tim support lalu dicari di log/APM. Format
error v1 sengaja tidak diubah — **mengganti format error adalah breaking change**.

## 1. Idempotency — retry yang aman

```bash
scripts/run.sh rest idempotency
```

Client hanya sabar 1 detik, server (lewat header demo `X-Simulate-Delay-Ms`) baru menjawab
setelah 2 detik **padahal debit sudah terjadi**. Hasil yang sudah diverifikasi:

```
=== A. Retry TANPA Idempotency-Key ===
[CLIENT] POST ... -> TIMEOUT -- client tidak tahu apakah transfer terjadi atau tidak
[CLIENT] POST ... -> 201 TRF-0002 (Idempotent-Replayed: false)
[CLIENT] Saldo berkurang 200000 -- seharusnya 100000  << DOUBLE DEBIT!

=== B. Retry DENGAN Idempotency-Key ===
[CLIENT] POST ... -> TIMEOUT
[CLIENT] POST ... -> 409 ... idempotency-in-progress   (retry terlalu cepat)
[CLIENT] POST ... -> 201 TRF-0003 (Idempotent-Replayed: true)
[CLIENT] Saldo berkurang 100000  << tepat sekali, aman

=== C. Key yang sama dipakai untuk request lain ===
[CLIENT] POST ... -> 422 ... idempotency-key-reuse
```

Yang perlu diperhatikan di `IdempotencyStore`:
- **`putIfAbsent` atomik** — dua retry yang datang bersamaan tidak boleh sama-sama lolos.
- Disimpan bersama **fingerprint body**: key sama + isi beda = bug client → 422.
- Hanya hasil **sukses** yang disimpan; gagal bisnis melepas key agar bisa dicoba lagi.
- Untuk production: store harus **shared** antar instance (Redis `SET NX` + TTL / unique
  constraint di DB), punya **TTL** (mis. 24 jam), dan key di-scope **per client**.
- Idempotency key ≠ `referenceId`. Key adalah kontrak **transport** ("ini request yang sama"),
  referenceId adalah identitas **bisnis**. Sistem bank biasanya memakai keduanya (key untuk
  retry, unique constraint referenceId sebagai jaring pengaman terakhir).

## 2. Method HTTP dan semantiknya

```bash
B=localhost:8080/v2/beneficiaries
curl -i -X POST $B -H 'Content-Type: application/json' \
     -d '{"accountNumber":"3030003","alias":"Citra","bankCode":"153"}'     # 201 + Location
curl -i -X PATCH $B/BNF-1 -H 'Content-Type: application/merge-patch+json' \
     -d '{"alias":"Citra L"}'                                              # bankCode TETAP 153
curl -i -X PUT $B/BNF-1 -H 'Content-Type: application/json' \
     -d '{"accountNumber":"3030003"}'                                      # alias & bankCode jadi null
curl -I $B/BNF-1                                                           # HEAD: header saja
curl -i -X OPTIONS $B/BNF-1                                                # Allow: DELETE,PUT,GET,HEAD,PATCH,OPTIONS
curl -i -X DELETE $B/BNF-1                                                 # 204
curl -i -X DELETE $B/BNF-1                                                 # 404 -- tetap idempotent
```

- **PUT vs PATCH**: PUT = kirim representasi lengkap, yang tidak dikirim ikut hilang. PATCH
  (JSON Merge Patch, RFC 7396) = hanya field yang dikirim yang berubah; kirim `null` untuk menghapus.
- **DELETE kedua 404** tidak melanggar idempotency — yang idempotent adalah **state server**,
  bukan status code-nya.
- **HEAD & OPTIONS** disediakan otomatis oleh Spring dari mapping GET dan daftar method yang ada.
  OPTIONS juga dipakai browser untuk CORS preflight (lihat modul CORS).
- **Custom method?** Jangan. Untuk aksi yang bukan CRUD, pakai sub-resource:
  `POST /v2/transfers/{id}/reversal`.

## 3. Status code & anti-pattern

```bash
curl -i -X POST localhost:8080/anti-pattern/transfers -H 'Content-Type: application/json' \
     -d '{"sourceAccount":"1","destinationAccount":"1","amount":5,"referenceId":"X"}'
# HTTP/1.1 200
# {"success":false,"errorCode":"E-042","errorMessage":"Transfer gagal"}
```

Bandingkan dengan `/v2/transfers` yang menjawab 422 problem+json untuk kasus yang sama.
Dengan pola "selalu 200", dashboard error-rate di gateway/APM selalu 0%, alert tidak pernah
berbunyi, dan retry policy HTTP client tidak bekerja.

## 4. Versioning & deprecation

Dua gaya di repo ini:
- **URL path** — `/transfers` (v1) vs `/v2/transfers`. Eksplisit, mudah di-route & di-log.
- **Header** — `GET /v2/accounts/{id}` dengan `X-API-Version: 1|2`. Tanpa header → v1, supaya
  client lama tetap jalan. v2 mengubah `balance` dari angka menjadi `{amount, currency}` —
  contoh breaking change yang **wajib** versi baru.

```bash
curl -i localhost:8080/v2/accounts/1010001                          # "balance":10000000
curl -i -H 'X-API-Version: 2' localhost:8080/v2/accounts/1010001     # "balance":{"amount":...,"currency":"IDR"}
curl -i localhost:8080/transfers/REF-REST-0001                       # Deprecation / Sunset / Link
```

Response header-versioned **harus** membawa `Vary: X-API-Version`; tanpa itu cache di tengah
jalan bisa menyajikan representasi v1 ke client v2.

## 5. Pagination — offset vs cursor

```bash
scripts/run.sh rest pagination
```

```
=== OFFSET pagination ===
[CLIENT] page 0: [TX-0017, TX-0015, TX-0013, TX-0012, TX-0011]
[CLIENT] ... sementara itu, transfer masuk ke 1010001
[CLIENT] page 1: [TX-0011, TX-0010, TX-0009, TX-0008, TX-0007]
[CLIENT] DUPLIKAT terlihat dua kali: [TX-0011]

=== CURSOR pagination ===
[CLIENT] halaman 1: [TX-0020, TX-0017, TX-0015, TX-0013, TX-0012]  nextCursor=c2VxOjEy
[CLIENT] halaman 2: [TX-0011, TX-0010, TX-0009, TX-0008, TX-0007]
[CLIENT] Tidak ada duplikat -- urutan stabil
```

| | Offset | Cursor / keyset |
|---|---|---|
| Lompat ke halaman N | Bisa | Tidak bisa |
| Total halaman | Murah | Mahal / tidak disediakan |
| Data baru saat membaca | Duplikat / terlewat | Stabil |
| Halaman sangat dalam | `OFFSET 100000` memindai 100k baris | `WHERE seq < ?` pakai index, konstan |
| Cocok untuk | Laporan admin "halaman 5 dari 20" | Infinite scroll mobile, sync, export |

Cursor sengaja **opaque** (base64) supaya client tidak bergantung pada isinya. Batas `size ≤ 50`
melindungi server dari request raksasa (dijawab 400).

## 6. Caching (ETag) & content negotiation

```bash
curl -i localhost:8080/v2/accounts/2020002                                   # ETag: "2020002-0-v1"
curl -i -H 'If-None-Match: "2020002-0-v1"' localhost:8080/v2/accounts/2020002 # 304, tanpa body
curl -H 'Accept: text/csv' 'localhost:8080/v2/accounts/1010001/transactions?size=3'
curl -i -H 'Accept: application/xml' localhost:8080/v2/accounts/1010001/transactions   # 406
```

ETag berubah setiap saldo berubah (dan berbeda per versi representasi). Content negotiation:
path yang sama, dua method controller dengan `produces` berbeda — Spring memilih berdasarkan `Accept`.

## Test

`V2ApiTest` mengunci semua perilaku di atas (double debit vs replay, 409/422 idempotency,
problem+json, 304, `Vary`, header deprecation, duplikat offset vs cursor, 406, PUT/PATCH/DELETE).

```bash
mvn -pl rest-demo test
```

## HTTP/2

Server ini juga melayani HTTP/2: h2c di port 8080 (`server.http2.enabled=true`) dan h2 via
TLS di port 8443 dengan profile `tls`. Latihannya ada di
[`docs/http2-lab.md`](../docs/http2-lab.md).
