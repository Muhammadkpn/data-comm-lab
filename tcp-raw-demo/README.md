# tcp-raw-demo

Belajar komunikasi **raw TCP socket** dengan skenario "transfer dana antar rekening" —
tanpa HTTP, tanpa framework. Tujuannya supaya kerasa apa yang biasanya "gratis" disediakan
HTTP (framing pesan, batas request/response) ternyata harus diurus sendiri di level TCP.

Kalau harus diringkas jadi satu masalah: **TCP tidak tahu di mana satu pesan berakhir.**
Hampir semua isi modul ini adalah cara menjawab masalah itu, dan akibatnya kalau jawabannya
salah.

## Peta file

Lima file, dan urutan membacanya berpengaruh. Disarankan dari atas ke bawah:

| # | File | Perannya | Kenapa dibaca di urutan ini |
|---|---|---|---|
| 1 | `TransferMessage.java` | POJO 4 field: source, destination, amount, referenceId | Paling sederhana. Ini "apa yang dikirim", belum ada urusan jaringan sama sekali |
| 2 | `MessageCodec.java` | **Inti modul ini.** Encode/decode pesan ↔ byte, termasuk framing | Di sinilah masalah batas pesan diselesaikan. Sisanya cuma memakai kelas ini |
| 3 | `TransferServer.java` | Buka port 9090, terima koneksi, baca 1 frame, balas 1 frame | Sisi penerima. Pendek, karena kerja beratnya sudah di `MessageCodec` |
| 4 | `TransferClient.java` | Buka koneksi, kirim 1 frame, tunggu 1 frame balasan | Sisi pengirim. Cermin dari server |
| 5 | `BadFramingDemo.java` | Contoh **negatif**: framing yang dibaca salah | Baca terakhir. Baru terasa gunanya setelah paham framing yang benar |

Kalau cuma sempat baca satu file: **`MessageCodec.java`**.

## Cara run

Butuh 2 terminal.

**Terminal 1 — jalankan server:**
```bash
mvn -pl tcp-raw-demo compile exec:java -Dexec.mainClass="com.learn.datacomm.tcpraw.TransferServer"
```

**Terminal 2 — jalankan client:**
```bash
mvn -pl tcp-raw-demo compile exec:java -Dexec.mainClass="com.learn.datacomm.tcpraw.TransferClient"
```

Server akan tetap listening setelah request pertama, jadi client bisa dijalankan berkali-kali.

### Demo framing yang salah

```bash
mvn -pl tcp-raw-demo compile exec:java -Dexec.mainClass="com.learn.datacomm.tcpraw.BadFramingDemo"
```

Program ini menjalankan server dan client "buggy" dalam satu proses, mengirim 2 pesan
back-to-back, lalu membaca dengan asumsi ukuran payload yang salah (bukan dari length
prefix yang sesungguhnya). Perhatikan output-nya: pesan jadi terpotong/nyasar (desync).

## Masalah inti: TCP tidak kenal batas pesan

TCP menjamin banyak hal: byte sampai **urut**, **tidak hilang**, dan **tidak duplikat**.
Satu hal yang TIDAK dijaminnya: di mana satu pesan berakhir dan pesan berikutnya dimulai.

Bagi TCP, yang mengalir hanyalah **aliran byte tanpa sekat** — bukan aliran pesan. Kalau
aplikasi memanggil "kirim" dua kali, TCP sama sekali tidak berjanji penerima akan mendapat
dua potongan yang rapi:

```
Yang dikirim aplikasi:      [ pesan A ][ pesan B ]

Yang mungkin diterima:
  kemungkinan 1:            [ pesan A ][ pesan B ]     <- kebetulan rapi
  kemungkinan 2:            [ pesan A + pesan B ]      <- digabung jadi satu (coalescing)
  kemungkinan 3:            [ pesan  ][ A + pesan B ]  <- terpecah di tempat acak
```

Ketiganya sah menurut TCP. Penggabungan dan pemecahan ini terjadi di luar kendali aplikasi:
tergantung ukuran buffer OS, MTU jaringan, dan optimasi pengiriman. Artinya, **satu kali
`read()` tidak sama dengan satu pesan** — asumsi itu adalah salah satu bug jaringan paling
umum.

Karena TCP tidak menyediakan sekat, aplikasi harus membuat sekatnya sendiri. Itulah
**framing**.

## Anatomi satu frame

Modul ini memakai skema framing paling umum: **length-prefixed framing**. Aturannya satu
kalimat — *sebelum isi pesan, kirim dulu berapa panjang isi pesan itu.*

```
[ 4 byte panjang payload ][ N byte payload ]
```

Contoh nyata dari pesan yang dikirim `TransferClient`:

```
TransferMessage{sourceAccount='1010001', destinationAccount='2020002',
                amount=150000, referenceId='REF-TCP-0001'}

  ke payload (pipe-delimited, UTF-8):
    "1010001|2020002|150000|REF-TCP-0001"          <- 35 byte

  jadi satu frame utuh di kabel:
    00 00 00 23  31 30 31 30 30 30 31 7c 32 30 ...
    \_________/  \_______________________________/
      4 byte                35 byte
      panjang               payload
      (0x23 = 35)           ("1010001|2020..." dalam ASCII)
```

Tiga hal yang membuat skema ini bekerja:

**1. Prefix panjangnya selalu tetap 4 byte.** `DataOutputStream.writeInt()` selalu menulis
tepat 4 byte, berapa pun nilainya — 35 ditulis sebagai `00 00 00 23`, bukan `23` saja.
Sifat "selalu 4 byte" ini penting: penerima tahu harus membaca tepat 4 byte lebih dulu,
tanpa perlu tahu apa-apa soal isi pesannya.

**2. Urutannya tidak boleh dibalik.** Panjang harus datang **sebelum** payload. Kalau
sesudah, penerima tidak akan tahu harus berhenti membaca di mana untuk menemukan panjangnya
— masalah ayam-dan-telur.

**3. `writeInt()` dan `readInt()` sepakat soal urutan byte.** Keduanya memakai *big-endian*
(byte paling signifikan dulu), dan karena keduanya bagian dari Java standar, kecocokannya
otomatis. Ini baru jadi masalah nyata kalau lawan bicaranya ditulis di bahasa/platform lain
yang memakai konvensi berbeda.

Dua arah encoding ini ada di satu tempat yang sama, `MessageCodec`:

| Method | Arah | Dipakai oleh |
|---|---|---|
| `writeMessage()` | `TransferMessage` → byte | `TransferClient` (kirim request) |
| `readMessage()` | byte → `TransferMessage` | `TransferServer` (terima request) |
| `writeResponse()` | `status, message` → byte | `TransferServer` (kirim balasan) |
| `readResponse()` | byte → `String[]` | `TransferClient` (terima balasan) |

Response memakai format yang lebih sederhana (`"STATUS|message"`) tapi **skema framing yang
persis sama** — 4 byte panjang, lalu payload. Framing dan format isi adalah dua lapis yang
terpisah.

## Alur komunikasi

```
CLIENT                                   SERVER
  |                                         |
  |--- buka koneksi TCP ------------------->|  new Socket(HOST, PORT)
  |                                         |  serverSocket.accept()
  |                                         |
  |--- [4 byte length][payload] ----------->|  MessageCodec.writeMessage()
  |                                         |     -> MessageCodec.readMessage()
  |                                         |        (baca 4 byte length dulu,
  |                                         |         baru baca N byte payload)
  |                                         |
  |                                         |  "proses" transfer
  |                                         |
  |<-- [4 byte length][payload] ------------|  MessageCodec.writeResponse()
  |                                         |     -> MessageCodec.readResponse()
  |                                         |
  |--- tutup koneksi ---------------------->|  try-with-resources
```

Yang perlu disadari: **urutan "client menulis dulu, baru server membalas" bukan aturan
TCP.** TCP itu full-duplex — kedua pihak boleh menulis kapan saja, bahkan bersamaan. Urutan
di atas murni kesepakatan antara `TransferClient` dan `TransferServer.handleClient()`.

Kalau kesepakatan itu dilanggar — misal client ikut menunggu di `readResponse()` sebelum
mengirim apa pun — kedua pihak akan saling menunggu selamanya. Tidak ada timeout bawaan,
tidak ada pesan error, prosesnya hanya diam. Di HTTP, urutan request-response sudah baku
sehingga kelas bug ini tidak ada.

## `readFully()` vs `read()`

Ini detail kecil yang gampang terlewat tapi menentukan benar-tidaknya framing.

`InputStream.read(buffer)` hanya menjamin mengembalikan **minimal 1 byte**. Dia TIDAK
menjamin mengisi buffer sampai penuh, walaupun pengirim sudah mengirim semuanya:

```java
byte[] payload = new byte[35];
int n = in.read(payload);   // n bisa 35, bisa juga 20, bisa 1
```

Kalau hasil `read()` langsung dianggap satu pesan utuh, pesan bisa terbaca terpotong —
padahal datanya tidak hilang, hanya **belum tiba** saat itu.

`MessageCodec.readMessage()` memakai `readFully()`:

```java
byte[] payloadBytes = new byte[length];
dis.readFully(payloadBytes);   // dijamin terisi PENUH, atau melempar EOFException
```

`readFully()` memanggil `read()` berulang kali di balik layar sampai buffer benar-benar
penuh. Kalau koneksi keburu putus di tengah, dia melempar `EOFException` — gagal dengan
jelas, bukan diam-diam mengembalikan data separuh.

Aturan praktisnya: **kalau sudah tahu persis harus membaca berapa byte, pakai
`readFully()`.** Dan berkat length prefix, di modul ini kita memang selalu tahu.

## Membaca `BadFramingDemo`

File ini sengaja salah, untuk menunjukkan akibat kalau framing tidak dipatuhi.

Sisi server-nya normal: mengirim dua pesan berturut-turut lewat `MessageCodec.writeMessage()`,
jadi dua frame yang keluar sebenarnya sudah benar formatnya. Yang bermasalah sisi client:

```java
dis.readInt();                            // (1) prefix dibaca, tapi nilainya DIBUANG

byte[] wrongSizedPayload = new byte[20];  // (2) ukuran ditebak 20, bukan dibaca dari prefix
int bytesRead = dis.read(wrongSizedPayload);
```

Dua kesalahan yang bekerja sama:

1. **`readInt()` dipanggil tapi hasilnya dibuang.** Posisi baca memang maju 4 byte dengan
   benar, tapi satu-satunya informasi "berapa byte payload yang harus dibaca" ikut terbuang.
2. **Ukuran payload di-hard-code 20 byte.** Ini menebak, bukan membaca. Selama panjang
   payload sebenarnya bukan 20, posisi baca pasti meleset.

Yang membuat ini berbahaya: **efeknya menumpuk.** Sisa byte frame pertama yang tidak terbaca
tetap tertinggal di stream, lalu ikut terbaca sebagai bagian dari frame berikutnya. Jadi
bukan hanya pesan pertama yang rusak — semua pesan sesudahnya ikut rusak, dan makin lama
makin jauh melesetnya. Inilah **desync**: pengirim dan penerima tidak lagi sepakat di mana
satu pesan berakhir.

Dan tidak ada yang memberi tahu. Tidak ada status code, tidak ada checksum, tidak ada
exception dari TCP — karena bagi TCP semua byte sudah terkirim dengan sempurna. Kesalahannya
murni ada di tafsir aplikasi.

Bandingkan dengan `TransferClient` yang memakai `MessageCodec.readMessage()`: ukuran buffer
diambil dari nilai `readInt()`, sehingga batas pesan selalu tepat.

> Hard-coding ukuran seperti ini terdengar mengada-ada, tapi di dunia nyata bentuknya lebih
> halus: ukuran yang benar saat protokol v1 lalu ada field baru di v2, atau panjang yang
> dihitung pakai `String.length()` (jumlah karakter) padahal yang dikirim byte UTF-8 —
> berbeda begitu ada karakter non-ASCII.

## Penjagaan di `MessageCodec`

`readMessage()` punya pemeriksaan yang mudah dilewati saat membaca sekilas:

```java
if (length < 0 || length > 10_000) {
    throw new IOException("Panjang payload tidak masuk akal: " + length +
            " -- kemungkinan besar framing salah baca posisi byte");
}
```

Kenapa perlu? Karena kalau posisi baca sudah geser, 4 byte yang terbaca sebagai "panjang"
sebenarnya adalah **potongan teks payload**. Empat karakter ASCII yang ditafsirkan sebagai
`int` 32-bit menghasilkan angka yang sangat besar — tiap karakter menempati satu byte penuh,
jadi hasilnya gampang mencapai ratusan juta.

Tanpa penjagaan ini, baris berikutnya (`new byte[length]`) akan mencoba mengalokasikan array
raksasa, dan program mati dengan `OutOfMemoryError` yang sama sekali tidak menjelaskan akar
masalahnya. Dengan penjagaan ini, pesan error-nya langsung menunjuk penyebab yang benar:
framing.

Ini pola yang berlaku umum di protokol biner: **selalu curigai length prefix sebelum
mempercayainya.** Di sistem yang menerima koneksi dari luar, batas semacam ini juga menjadi
pertahanan dasar — tanpanya, satu pesan berisi angka panjang yang besar sudah cukup untuk
menghabiskan memori server.

## Yang perlu diperhatikan di kode

- **`MessageCodec`** — inti pembelajaran modul ini. TCP adalah stream of bytes tanpa
  konsep batas pesan bawaan, jadi kita definisikan sendiri skema **length-prefixed
  framing**: 4 byte pertama menyatakan panjang payload, baru diikuti payload itu sendiri.
- **`readFully()`** dipakai (bukan `read()` biasa) karena satu panggilan `read()` di
  socket tidak menjamin dapat semua byte yang diminta sekaligus.
- **Pemisahan tanggung jawab** — `TransferMessage` mengurus *apa* isinya (POJO murni, tidak
  tahu-menahu soal socket), `MessageCodec` mengurus *bagaimana* mengirimnya. Di `rest-demo`
  pembagian ini juga ada, hanya saja peran `MessageCodec` dipegang Jackson.
- **`TransferServer`** memakai model **satu thread per koneksi** (`new Thread(...)` di dalam
  loop `accept()`). Paling sederhana untuk dibaca, dan cukup untuk demo — tapi tidak untuk
  skala produksi, karena tiap koneksi memakan satu thread OS. Ini juga alasan lahirnya
  NIO/event-loop, tapi itu di luar scope modul ini.
- **`BadFramingDemo`** — menunjukkan konsekuensi kalau asumsi framing salah: pesan bisa
  terbaca terpotong atau tercampur dengan pesan berikutnya (desync), dan tidak ada
  mekanisme protokol (seperti status code HTTP) yang otomatis memberi tahu error ini.
- Tidak ada validasi/error handling berlapis — fokus ke mekanisme framing itu sendiri.

## Dibandingkan modul lain

Modul ini paling dasar di repo. Semua modul lain berdiri di atas hal yang di sini dikerjakan
manual — inilah yang membuat pekerjaan `MessageCodec` jadi tidak terlihat di modul tetangga:

| Urusan | `tcp-raw-demo` | `rest-demo` | `grpc-demo` |
|---|---|---|---|
| **Batas pesan (framing)** | ditulis tangan di `MessageCodec` | `Content-Length` / chunked, diurus HTTP | panjang message diurus HTTP/2 + protobuf |
| **Serialisasi** | `String.join("\|")` + `split()` manual | Jackson (JSON ↔ objek) | protobuf (generated) |
| **Menandai sukses/gagal** | string bebas `"SUCCESS\|..."` | HTTP status code (201, 400, 422) | `io.grpc.Status` |
| **Banyak operasi di 1 server** | tidak ada — 1 koneksi = 1 operasi | routing via method + path | banyak `rpc` dalam satu `service` |
| **Kontrak antar pihak** | hanya di kepala/dokumentasi | implisit dari DTO + anotasi | eksplisit di file `.proto` |
| **Kalau kontrak dilanggar** | diam-diam salah baca (desync) | 400/422 saat runtime | sebagian gagal saat compile |

Baris terakhir yang paling menjelaskan posisi modul ini: di raw TCP, salah paham soal format
**tidak menghasilkan error apa pun** — hanya data yang tafsirannya melenceng. Makin ke kanan
di tabel itu, makin banyak kesalahan yang ditangkap lebih awal, dengan harga makin banyak
aturan yang harus diikuti.
