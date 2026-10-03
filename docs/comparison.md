# Perbandingan lintas protokol

Semua modul memakai skenario yang sama, **transfer dana antar rekening**. Halaman ini
merangkum perbedaannya dalam satu tempat.

## 1. Ukuran payload — diukur, bukan diperkirakan

```bash
scripts/run.sh compare sizes
```

`PayloadSizeComparison` meng-encode transfer yang **sama** (`1234567890 → 9876543210`,
`amount=150000`, `ref=000123`) memakai **codec asli** dari tiap modul:

```
Format         Modul            Byte  vs min  Self-describing
Protobuf       grpc-demo          36    1.0x  tidak
Raw TCP frame  tcp-raw-demo       39    1.1x  tidak
OFS native     ofs-demo           53    1.5x  tidak
JSON           rest-demo         103    2.9x  ya
ISO 8583       iso8583-demo      104    2.9x  tidak
OFS XML        ofs-demo          328    9.1x  ya
```

Cara membacanya:
- **Protobuf paling kecil** karena field diidentifikasi **nomor** (1 byte tag), bukan nama,
  dan angka di-encode varint (`150000` → 3 byte).
- **JSON ≈ 3× protobuf** karena nama field (`"destinationAccount"`) ikut di **setiap** pesan.
  Itu harga dari self-describing: bisa dibaca manusia dan di-parse tanpa skema.
- **ISO 8583 tidak sekecil dugaan** di sini karena packager demo memakai encoding ASCII
  (`IFA_*`), bitmap ditulis sebagai hex ASCII (32 karakter untuk primary + secondary bitmap,
  karena DE 102 > 64), dan pesan membawa DE tambahan (processing code, STAN, RRN, terminal
  id). Packager biner (`IFB_*`) di jaringan switching nyata jauh lebih ringkas.
- **XML 9×** karena tag buka **dan** tutup per field, plus header deklarasi.

Ukuran di atas hanya **body**. Overhead transport di atasnya:

| Transport | Overhead per pesan (kira-kira) |
|---|---|
| Raw TCP (`tcp-raw-demo`) | 4 byte length prefix (sudah termasuk di tabel) |
| HTTP/1.1 (`rest-demo`) | Request line + header teks, biasanya **ratusan byte**, sering lebih besar dari body |
| HTTP/2 (`grpc-demo`, h2c) | Frame header 9 byte + header terkompresi HPACK (header berulang jadi beberapa byte) |
| UDP (`udp-demo`) | 8 byte header UDP, tanpa koneksi |

Untuk pesan kecil seperti transfer, **header HTTP/1.1 sering lebih besar dari body-nya**.
Di situ HTTP/2 + protobuf (gRPC) unggul jauh.

## 2. Kontrak & evolusi skema

| | REST/JSON | gRPC/Protobuf | GraphQL | ISO 8583 | OFS |
|---|---|---|---|---|---|
| Kontrak | Opsional (OpenAPI) | **Wajib** `.proto` | **Wajib** SDL | Spesifikasi + packager | Spesifikasi vendor |
| Kapan drift ketahuan | Runtime | **Compile time** (sebagian) | Runtime (validasi query) | Runtime (unpack gagal / salah baca) | Runtime |
| Field asing | Diabaikan diam-diam | Disimpan sebagai unknown field | Error validasi | Bitmap tidak dikenal | Salah posisi |
| Demo | `rest-demo` "Ketika kontrak berubah" | `grpc-demo/schema-break-demo.sh` | `graphql-demo` | `iso8583-demo` | `ofs-demo` |

## 3. Pola interaksi

| Pola | Modul |
|---|---|
| Request–response sinkron | `tcp-raw-demo`, `rest-demo`, `grpc-demo` (unary), `iso8583-demo`, `ofs-demo` |
| Server streaming | `grpc-demo` (`WatchTransferProgress`), `realtime-demo` (SSE) |
| Dua arah | `realtime-demo` (WebSocket) |
| Client memilih bentuk data | `graphql-demo` |
| Fire-and-forget | `udp-demo` skenario A |

## 4. Kapan memilih apa

| Kebutuhan | Pilihan wajar | Kenapa |
|---|---|---|
| API publik / mobile / browser | REST + JSON | Universal, mudah di-debug & di-cache, tooling matang |
| Antar-service internal, latensi & throughput penting, polyglot | gRPC | Kontrak ketat, biner, HTTP/2, streaming |
| Banyak jenis client dengan kebutuhan data berbeda | GraphQL (sering sebagai BFF) | Satu round-trip, field sesuai kebutuhan |
| Status / notifikasi real-time ke client | SSE (atau WebSocket bila dua arah) | Push tanpa polling |
| Switching kartu / ATM / EDC | ISO 8583 | Standar industri, sudah diimplementasikan semua pihak |
| Integrasi core banking (T24/FLEXCUBE) | OFS / format vendor | Tidak ada pilihan lain, adaptasi di layer integrasi |
| Media real-time, telemetri | UDP (atau QUIC) | Data terlambat sama dengan data hilang |
| Event antar-service, decoupling | Message broker (Kafka) | Async, replay, banyak consumer (belum ada demo di repo ini) |

Prinsip yang sama berlaku di semua baris: **tidak ada protokol yang paling baik**, yang ada
trade-off yang cocok untuk konteksnya. Ketika ditanya "kenapa tidak pakai X?", jawaban
senior adalah cost-benefit-nya, bukan preferensi.
