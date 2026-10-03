# udp-demo

Pasangan kontras untuk `tcp-raw-demo` (bab 15 materi): skenario transfer yang sama, tapi
di atas **UDP**. Tujuannya merasakan sendiri apa saja yang selama ini "gratis" dari TCP.

| | TCP (`tcp-raw-demo`) | UDP (modul ini) |
|---|---|---|
| Koneksi | Handshake, `accept()` per client | Tidak ada. Satu socket menerima dari siapa saja |
| Batas pesan | **Tidak ada**, jadi butuh framing (length prefix) | **Ada**: 1 `send()` = 1 datagram = 1 `receive()` |
| Sampai? | Dijamin (retransmisi di kernel) atau koneksi error | Tidak dijamin. `send()` selalu "sukses" |
| Urutan | Dijamin | Tidak dijamin |
| Duplikat | Tidak pernah | Mungkin |
| Pesan terlalu besar | Sisa byte menunggu `read()` berikutnya | Sisa byte **dibuang diam-diam** |

## Cara run

**Terminal 1 — server** (port 9092, simulasi 30% paket hilang):
```bash
scripts/run.sh udp server
```
Localhost hampir tidak pernah kehilangan paket, jadi server **mensimulasikan** jaringan
buruk: request masuk dan ack keluar masing-masing dibuang dengan peluang 30%, dan setiap
ack ditunda acak 0–150 ms (penyebab urutan teracak). Seed random tetap, jadi hasilnya bisa
diulang.

**Terminal 2 — client:**
```bash
scripts/run.sh udp client
```

## Hasil yang sudah diverifikasi

```
=== A. Fire-and-forget (seperti UDP apa adanya) ===
[CLIENT] 10 datagram terkirim (send() selalu 'sukses' -- UDP tidak tahu apakah sampai)
[CLIENT] Urutan ack yang diterima : [3, 4, 6, 1, 9, 2, 5]
[CLIENT] Tidak ada kabar (hilang)  : [7, 8, 10]  -- client tidak tahu: request yang hilang, atau ack-nya?

=== B. Stop-and-wait + retransmisi (keandalan dibangun sendiri) ===
[CLIENT]   timeout -> kirim ulang seq=103 (percobaan 2)
[CLIENT] seq=103 -> SUCCESS
...
[CLIENT]   timeout -> kirim ulang seq=105 (percobaan 2)
[CLIENT] seq=105 -> SUCCESS
...
[CLIENT] Berhasil 10/10 dengan 4 retransmisi

=== C. Datagram lebih besar dari buffer penerima ===
[CLIENT] Terkirim 1 datagram 2000 byte; server membaca dengan buffer 1024 byte.
```

Log server untuk skenario yang sama:

```
[SERVER] seq=7 diproses ...
[SERVER] (simulasi) ack seq=7 HILANG di jaringan        <- transfer TERJADI, tapi client tidak tahu
[SERVER] (simulasi) request seq=10 HILANG di jaringan   <- transfer TIDAK terjadi, client juga tidak tahu
...
[SERVER] (simulasi) request seq=103 HILANG di jaringan
[SERVER] seq=103 diproses                                <- retransmisi berhasil
[SERVER] seq=105 diproses
[SERVER] (simulasi) ack seq=105 HILANG di jaringan
[SERVER] seq=105 DUPLIKAT (B-167-5 sudah diproses) -> kirim ulang ack saja
[SERVER] Datagram tidak dikenal (1024 byte), diabaikan   <- skenario C: terpotong
```

## Yang dipelajari

1. **Skenario A — dari sisi client, "hilang" itu ambigu.** Seq 7 & 8 sudah diproses
   (ack-nya yang hilang), seq 10 tidak pernah sampai. Client melihat keduanya sama persis:
   tidak ada kabar. Masalah ini identik dengan **timeout HTTP** di `rest-demo`
   (`IdempotencyClient`).
2. **Skenario B — keandalan bisa dibangun sendiri** (`ReliableSender`: stop-and-wait,
   timeout, retransmisi). Retransmisi karena ack hilang membuat request yang sama tiba dua
   kali, jadi **server wajib idempotent** (dedupe per `referenceId`). Pola ini sama dengan
   `Idempotency-Key` di REST dan sama dengan cara TCP membuang segmen duplikat.
3. **Skenario C — batas datagram dijaga, tapi ukurannya terbatas.** Kalau buffer penerima
   lebih kecil, sisanya hilang tanpa error. Datagram di atas MTU (~1500 byte di Ethernet)
   juga akan difragmentasi di level IP, dan satu fragmen hilang berarti seluruh datagram
   hilang. Karena itu protokol UDP menjaga pesannya tetap kecil.

## Kapan memilih UDP?

UDP dipilih ketika **data terlambat sama buruknya dengan data hilang**, atau ketika aplikasi
ingin mengatur keandalannya sendiri:

- **DNS** — satu pertanyaan, satu jawaban kecil; kalau hilang, tanya lagi.
- **VoIP / video call / game** — frame yang terlambat tidak berguna; lebih baik lanjut.
- **QUIC / HTTP/3** — keandalan, enkripsi, dan multiplexing dibangun di user space di atas UDP.
  Ini menghindari *head-of-line blocking* TCP: satu paket hilang tidak menahan stream lain.
- **Telemetri / metrics (StatsD)** — kehilangan sedikit sampel bisa diterima.

Untuk transaksi keuangan, keandalan, urutan, dan exactly-once processing wajib, jadi
TCP (atau protokol di atasnya) tetap pilihan. Konteks Aira: semua komunikasi berbasis TCP,
UDP belum dibutuhkan.

## Yang perlu diperhatikan di kode

- **`TransferDatagram`** — format pesan tanpa length prefix. Bandingkan dengan `MessageCodec`
  di `tcp-raw-demo`. Perhatikan juga `getLength()`: buffer bisa berisi sisa data lama.
- **`UdpTransferServer`** — tidak ada `accept()`; simulasi loss/reorder; dedupe `referenceId`.
- **`ReliableSender`** — stop-and-wait ARQ; ack untuk seq lama (terlambat) diabaikan.
- **`UdpDemoTest`** — loss 30% tetap menghasilkan 20/20 transfer yang diproses tepat sekali;
  batas datagram; truncation.
