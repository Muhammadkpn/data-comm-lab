# grpc-demo

Belajar **gRPC** dengan skenario yang sama: transfer dana antar rekening. Fokus:
perbedaan unary vs server streaming, protobuf schema, dan error handling ala gRPC
(`io.grpc.Status`).

## Cara run

Butuh 2 terminal. Maven akan otomatis men-generate kode Java dari `transfer.proto`
saat `compile` (lewat `protobuf-maven-plugin`) -- tidak perlu langkah manual.

**Terminal 1 — jalankan server:**
```bash
mvn -pl grpc-demo compile exec:java -Dexec.mainClass="com.learn.datacomm.grpc.TransferServer"
```

**Terminal 2 — jalankan client:**
```bash
mvn -pl grpc-demo compile exec:java -Dexec.mainClass="com.learn.datacomm.grpc.TransferClient"
```

Client akan menjalankan 3 skenario: unary happy path, unary error case, dan server
streaming (menerima 4 update progress berturut-turut dari satu request).

## Alur komunikasi

**Unary RPC** (mirip request-response REST biasa):
```
CLIENT                                   SERVER
  |--- TransferRequest (protobuf) ------->|
  |                                       |  proses...
  |<-- TransferResponse (protobuf) -------|
```

**Server streaming RPC** (satu request, banyak response dalam satu koneksi):
```
CLIENT                                   SERVER
  |--- TransferRequest ------------------>|
  |<-- TransferStatusUpdate (1/4) --------|
  |<-- TransferStatusUpdate (2/4) --------|
  |<-- TransferStatusUpdate (3/4) --------|
  |<-- TransferStatusUpdate (4/4) --------|
  |<-- [stream ditutup: onCompleted] -----|
```

Semua ini berjalan di atas **satu koneksi HTTP/2**, bukan berkali-kali request
seperti polling REST.

## Kode yang ditulis manual vs kode hasil generate

Ini yang paling sering bikin bingung pertama kali pakai gRPC, karena sebagian besar
"kode" yang benar-benar dieksekusi TIDAK ada di `src/` — baru muncul setelah `mvn compile`.

**Yang ditulis manual (ada di `src/main/`):**
```
src/main/proto/transfer.proto              <- satu-satunya "sumber kebenaran" skema
src/main/java/.../TransferServiceImpl.java  <- implementasi logic server
src/main/java/.../TransferServer.java       <- kode buka port & daftarkan service
src/main/java/.../TransferClient.java       <- kode pemanggil RPC
```

**Yang di-generate otomatis (muncul di `target/generated-sources/` setelah `mvn compile`,
JANGAN diedit manual karena akan tertimpa lagi tiap kali compile):**
```
target/generated-sources/protobuf/java/.../TransferRequest.java
target/generated-sources/protobuf/java/.../TransferResponse.java
target/generated-sources/protobuf/java/.../TransferStatusUpdate.java
target/generated-sources/protobuf/java/.../TransferProto.java
target/generated-sources/protobuf/grpc-java/.../TransferServiceGrpc.java
```

Cara kerjanya, saat `mvn compile` dijalankan:

1. **`protobuf-maven-plugin`** membaca `transfer.proto`, lalu memanggil dua tool secara
   berurutan:
   - `protoc` (compiler protobuf asli, bukan Java) → men-generate class Java untuk tiap
     `message` di proto (`TransferRequest`, `TransferResponse`, `TransferStatusUpdate`).
     Class-class ini berisi builder pattern (`TransferRequest.newBuilder()...build()`)
     dan logic serialize/deserialize ke format biner protobuf.
   - plugin `protoc-gen-grpc-java` → khusus men-generate `TransferServiceGrpc.java` dari
     blok `service { ... }` di proto. File inilah yang berisi kerangka RPC-nya.
2. Kedua tool ini didownload otomatis oleh Maven sesuai OS kamu (lihat `os-maven-plugin`
   dan `protocArtifact`/`pluginArtifact` di `pom.xml`) — bukan yang perlu diinstall manual.
3. Hasil generate ditaruh di `target/generated-sources/`, lalu Maven menambahkannya
   sebagai source folder tambahan supaya ikut ter-compile bareng kode di `src/`.

Isi penting di dalam `TransferServiceGrpc.java` (generated) yang dipakai kode manual kita:

- **`TransferServiceImplBase`** — abstract class dengan method `transfer(...)` dan
  `watchTransferProgress(...)` yang sudah ada signature-nya (sesuai `service` di proto),
  tapi badan method-nya kosong/default. `TransferServiceImpl.java` (yang kita tulis)
  meng-`extends` class ini dan meng-`@Override` kedua method itu — itulah kenapa
  signature-nya harus persis cocok, karena "kontrak"-nya sudah ditentukan dari proto.
- **`TransferServiceBlockingStub`** — class yang dipakai `TransferClient.java` lewat
  `TransferServiceGrpc.newBlockingStub(channel)`. Method `.transfer(request)` dan
  `.watchTransferProgress(request)` di stub inilah yang benar-benar melakukan network
  call (serialize request → kirim lewat HTTP/2 → tunggu → deserialize response) — kode
  kita tinggal panggil seperti method Java biasa, jaringan-nya disembunyikan di sini.

Singkatnya: **proto = kontrak, generated code = "pipa" penghubung otomatis (serialisasi +
transport), kode manual = logic bisnis** yang mengisi pipa itu dari sisi server dan
memanggilnya dari sisi client.

Kalau proto diubah (misal tambah field baru), jalankan `mvn compile` ulang — class
generated akan diperbarui otomatis mengikuti proto, tidak perlu diedit tangan.

### Analogi ke rest-demo (Spring MVC)

Kalau sudah familiar dengan `rest-demo` di project ini, potongan-potongan gRPC di atas
punya padanan yang cukup dekat di dunia Spring MVC — bedanya di gRPC padanan itu
di-generate dari proto, bukan ditulis tangan pakai anotasi:

| gRPC                                      | Padanan di `rest-demo` (Spring MVC)                     | Peran                                                        |
|--------------------------------------------|----------------------------------------------------------|---------------------------------------------------------------|
| `transfer.proto`                           | Tidak ada file tunggal — tersirat dari `@PostMapping("/transfers")` + `TransferRequest`/`TransferResponse` | Kontrak: operasi apa saja yang tersedia, dan bentuk request/response-nya |
| `TransferRequest`, `TransferResponse` (generated) | `TransferRequest`/`TransferResponse` DTO (`rest-demo/dto/`) yang kamu tulis manual | Bentuk data yang keluar-masuk |
| `TransferServiceImplBase` (generated abstract class) | `@RestController` + `@RequestMapping("/transfers")` (anotasi di class) | "Slot" yang menandai class ini adalah tempat implementasi endpoint |
| `TransferServiceImpl.transfer(...)` (kamu tulis, `@Override`) | `TransferController.transfer(...)` (kamu tulis, `@PostMapping`) | **Controller method** — isinya logic bisnis yang sama persis: cek source==destination, susun response |
| `TransferServiceImpl.watchTransferProgress(...)` | Tidak ada padanan langsung yang simpel — butuh SSE/WebSocket terpisah untuk push bertahap | Server streaming |
| `TransferServiceGrpc.newBlockingStub(channel)` | `RestTemplate` (dipakai di `rest-demo/client/TransferClient.java`) | Klien: cara "manggil" endpoint dari kode Java |
| protobuf wire format (biner)               | Jackson (JSON ↔ Java object)                              | Serialisasi otomatis, sama-sama tidak perlu kamu tulis parsing manual |
| `io.grpc.Status.INVALID_ARGUMENT`          | `HttpStatus` / `ResponseEntity.status(...)`                | Cara menyatakan hasil sukses/gagal ke client |

Perbedaan konsep yang penting supaya analoginya tidak terlalu disamakan: di Spring MVC,
`@PostMapping`/`@RequestMapping` itu **anotasi di kode yang kamu tulis sendiri** — Spring
membaca anotasi itu saat aplikasi start (reflection), tidak ada tahap "generate file baru".
Di gRPC, urutannya kebalik: **proto dulu ditulis, lalu generate dulu, baru kamu isi
bagian yang generated itu**. `TransferServiceImplBase` sudah "ada" sebagai file `.java`
sebelum kamu tulis satu baris pun `TransferServiceImpl.java` — mirip seperti kalau Spring
menyediakan interface controller yang sudah jadi dari OpenAPI spec, dan kamu tinggal
`implements` lalu isi method-nya (pola ini sebenarnya juga ada di REST, disebut
"contract-first" / `openapi-generator`, tapi bukan default Spring MVC yang biasa dipakai
"code-first" seperti di `rest-demo`).

## Yang perlu diperhatikan di kode

- **`transfer.proto`** — kontrak schema-first. Dari sini di-generate class Java untuk
  message dan stub client/server. Field number (`= 1`, `= 2`, dst) menentukan encoding
  di wire format protobuf, bukan sekadar penomoran dekoratif.
- **`TransferServiceImpl`** — dua method RPC menunjukkan pola yang beda: `transfer()`
  memanggil `onNext()` sekali lalu `onCompleted()` (unary), sementara
  `watchTransferProgress()` memanggil `onNext()` berkali-kali sebelum `onCompleted()`
  (streaming).
- **Error handling** — gRPC punya set status code sendiri (`Status.INVALID_ARGUMENT`,
  dst), dikirim lewat trailer HTTP/2, terpisah dari isi response. Ini beda dari REST yang
  memakai HTTP status code, dan beda dari raw TCP yang statusnya cuma string bebas.
- Dibandingkan `rest-demo`: tidak ada polling untuk dapat update bertahap — server bisa
  langsung "push" lewat stream yang sama.

## Ketika kontrak berubah: compile time vs runtime

Bagian ini menjawab pertanyaan "apa untung-ruginya schema-first dibanding REST yang
fleksibel?" — bukan lewat teori, tapi lewat dua demo yang bisa dijalankan sendiri.

Ada **tiga** kelas kegagalan yang perlu dibedakan, dan ini yang sering tercampur:

| Kelas | Kapan ketahuan | Contoh |
|---|---|---|
| Kode tidak selaras dengan skema | **Compile time** | proto di-rename, kode Java belum menyesuaikan |
| Byte rusak / terpotong | Runtime, dengan exception | `InvalidProtocolBufferException` |
| Versi skema antar proses berbeda | Runtime, **diam-diam** | client pakai proto lama, server pakai proto baru |

gRPC unggul telak di kelas pertama. Yang jarang dibahas: gRPC **tidak** kebal di kelas
ketiga, dan di sana kegagalannya justru lebih senyap daripada REST.

### Demo 1 — kegagalan saat compile (`schema-break-demo.sh`)

```bash
./grpc-demo/schema-break-demo.sh
```

Script ini mengubah `transfer.proto` sementara (rename / hapus / ganti tipe / tambah
field) **tanpa menyentuh kode Java sama sekali**, lalu menjalankan `mvn compile` untuk
tiap perubahan. `transfer.proto` selalu dikembalikan ke kondisi semula lewat `trap EXIT`,
jadi aman dijalankan berulang kali — termasuk kalau di-Ctrl+C di tengah.

Hasil yang sudah diverifikasi:

| Perubahan di proto | Hasil `mvn compile` |
|---|---|
| Rename `reference_id` → `trace_id` | **BUILD FAILURE** — 10 error `cannot find symbol` |
| Hapus `destination_account` | **BUILD FAILURE** — 8 error `cannot find symbol` |
| Ganti `int64 amount` → `string amount` | **BUILD FAILURE** — 4 error `incompatible types` |
| Tambah `string currency = 5` | **BUILD SUCCESS** — perubahan additive itu aman |

Yang penting diperhatikan: error-nya muncul di **`TransferClient` DAN `TransferServiceImpl`
sekaligus**, lengkap dengan nomor baris. Satu perubahan di kontrak langsung menunjuk semua
tempat yang harus ikut diperbaiki, sebelum aplikasi sempat dijalankan. Inilah arti konkret
"risiko error pindah dari runtime ke compile time".

Baris terakhir tabel juga penting supaya kesimpulannya tidak berlebihan: **tidak semua
perubahan proto itu merusak.** Menambah field baru justru cara resmi meng-evolve kontrak
protobuf tanpa memutus client lama.

> **Catatan teknis.** `protobuf-maven-plugin` 0.6.1 kadang merusak cache-nya sendiri di
> `target/protoc-dependencies` (gagal dengan "Proto path element is not a directory", atau
> tidak menghasilkan file generated sama sekali). Itu bug plugin, bukan hasil demo.
>
> Script menanganinya dengan dua pengaman: mengulang compile khusus untuk error plugin, dan
> memverifikasi kode generated benar-benar mencerminkan proto yang barusan dipatch sebelum
> hasil build ditafsirkan. Kalau verifikasi itu tidak lolos, skenarionya ditandai
> **`DILEWATI (generated basi)`** — artinya "tidak ada kesimpulan yang bisa ditarik dari run
> ini", bukan berarti demonya salah. Jalankan ulang script-nya dan skenario itu akan
> memberi hasil. Prinsipnya: lebih baik melewatkan satu skenario daripada menampilkan
> kesimpulan yang keliru.

### Demo 2 — kegagalan diam-diam saat runtime (`SchemaDriftDemo`)

```bash
mvn -pl grpc-demo compile exec:java \
    -Dexec.mainClass="com.learn.datacomm.grpc.SchemaDriftDemo"
```

Demo ini tidak butuh server jalan. Isinya: pesan dibuat memakai proto **v1**
(`src/main/proto/drift/transfer_v1.proto`) lalu dibaca memakai proto **v2**
(`transfer_v2.proto`) — meniru client lama yang belum di-rebuild bicara dengan server baru.
Keduanya compile bersih, karena masing-masing hanya tahu versinya sendiri.

Kuncinya satu fakta wire format protobuf:

> **Nama field tidak pernah dikirim.** Yang dikirim hanya **nomor field** + tipe wire + nilai.

Hasil yang sudah diverifikasi:

| Perubahan di v2 | Yang terjadi | Exception? |
|---|---|---|
| Rename `reference_id` → `trace_id` (nomor tetap 4) | Nilai tetap **benar** | Tidak |
| Hapus field 2 | Masuk `getUnknownFields()`, **ikut terkirim lagi** saat re-serialize (37 byte → 37 byte) | Tidak |
| `int64` → `string` (wire type beda) | Diam-diam jadi **string kosong** | **Tidak** |
| `int32` → `int64` (wire type sama) | Diam-diam **benar** (42 → 42) | Tidak |
| Tambah field baru | Jadi default (`""` / 0) | Tidak |
| Nomor field dipakai ulang | **Data salah arti**: `2020002` terbaca sebagai nama bank | Tidak |
| Byte terpotong / korup | `InvalidProtocolBufferException` | **Ya** |

Tiga hal yang melawan intuisi dan paling layak diingat:

1. **Ganti tipe data TIDAK melempar exception.** `int64` → `string` hanya menghasilkan
   string kosong; nominal Rp150.000 lenyap tanpa satu pun log error. Kalau server
   menganggap kosong = 0, transfer diproses dengan nilai 0 dan semuanya tampak normal.
2. **Field yang dihapus tidak benar-benar hilang.** Byte-nya menempel di unknown fields dan
   ikut terkirim lagi ke hilir — service perantara bisa meneruskan data yang tak bisa ia lihat.
3. **Yang benar-benar melempar exception hanya byte korup.** Jadi "tidak ada error" sama
   sekali bukan bukti datanya benar.

### Aturan praktis meng-evolve `.proto`

**Aman:**
- Menambah field baru (pakai nomor yang belum pernah dipakai).
- Mengganti **nama** field (nama tidak ada di wire) — hanya memaksa rebuild kode.
- Melebarkan tipe dengan wire type sama: `int32` ↔ `int64` ↔ `uint32` ↔ `bool` (varint).

**Berbahaya:**
- Mengganti tipe ke wire type berbeda (`int64` ↔ `string`) → nilai hilang diam-diam.
- **Mengubah nomor field** → sama saja menukar semua data.
- Menghapus field lalu **memakai ulang nomornya** → data lama salah arti, tanpa error.

**Karena itu ada `reserved`.** Saat menghapus field, kunci nomornya:

```proto
message TransferRequest {
  reserved 2;
  reserved "destination_account";

  string source_account = 1;
  int64 amount = 3;
}
```

`protoc` akan **menolak compile** kalau ada yang memakai nomor 2 lagi — artinya kesalahan
paling berbahaya tadi dikembalikan lagi menjadi error compile time. Lihat message
`ReuseNumberSafe` di `transfer_v2.proto`.

### Kesimpulan: gRPC vs REST soal fleksibilitas

Untuk melihat sisi sebaliknya, jalankan pembandingnya di `rest-demo`:

```bash
mvn -pl rest-demo compile exec:java \
    -Dexec.mainClass="com.learn.datacomm.rest.client.SchemaDriftClient"
```

| Perubahan | gRPC | REST |
|---|---|---|
| Rename field | **BUILD FAILURE** | compile OK → 400 saat runtime |
| Hapus field | **BUILD FAILURE** | compile OK → 400 saat runtime |
| Ganti tipe (jadi teks) | **BUILD FAILURE** | compile OK → 400 saat runtime |
| Tambah field | BUILD SUCCESS | compile OK → 201, diabaikan diam-diam |
| Desimal `150000.99` ke `long` | **BUILD FAILURE** | compile OK → **201, dipotong jadi 150000** |

Bacaan yang jujur ke dua arah:

- **gRPC memang memindahkan error ke compile time** — tapi hanya untuk keselarasan
  **kode ↔ skema di modul yang di-build ulang**. Itu batas jaminannya.
- **Harganya adalah kekakuan**: perubahan kecil di kontrak memaksa semua pihak rebuild.
  Di REST, client dan server boleh beda versi tanpa rebuild serentak — keunggulan nyata
  kalau sistemnya besar dan tidak semua tim bisa deploy bersamaan.
- **Keduanya sama-sama bisa gagal diam-diam.** Di REST, `150000.99` dipotong jadi `150000`
  dan tetap dijawab 201. Di gRPC, `int64` → `string` jadi kosong tanpa error. "Diam-diam
  salah" bukan penyakit khas salah satu — bentuknya saja yang beda.
