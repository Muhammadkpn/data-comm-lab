# iso8583-demo

Belajar **ISO 8583** (format pesan standar industri finansial/perbankan, dipakai luas
di switching kartu ATM/EDC) pakai library [jPOS](https://jpos.org), dengan skenario yang
sama: transfer dana antar rekening. Fokus: struktur MTI + bitmap + data element, dan cara
pack/unpack.

## Cara run

Butuh 2 terminal.

**Terminal 1 — jalankan server:**
```bash
mvn -pl iso8583-demo compile exec:java -Dexec.mainClass="com.learn.datacomm.iso8583.TransferServer"
```

**Terminal 2 — jalankan client:**
```bash
mvn -pl iso8583-demo compile exec:java -Dexec.mainClass="com.learn.datacomm.iso8583.TransferClient"
```

Client menjalankan 2 skenario: transfer sukses (DE39=00) dan transfer ditolak
(DE39=96, karena source == destination).

## Alur komunikasi

```
CLIENT                                            SERVER
  |--- [2 byte length][ISO 8583 message] --------->|   MTI 0200 (Financial Request)
  |                                                 |   bitmap menandai DE mana yang ada
  |                                                 |   DE2=source, DE4=amount,
  |                                                 |   DE11=STAN, DE102=destination
  |                                                 |
  |<-- [2 byte length][ISO 8583 message] -----------|   MTI 0210 (Financial Response)
  |                                                 |   DE39=response code (00=approved)
```

## Struktur pesan ISO 8583 di modul ini

| Bagian  | Isi                                                             |
|---------|------------------------------------------------------------------|
| MTI     | 4 digit: versi + kelas pesan + fungsi + originator (mis. `0200`) |
| Bitmap  | Menandai Data Element (DE) mana saja yang hadir di pesan ini     |
| DE 2    | Primary Account Number — dipakai sebagai source account          |
| DE 3    | Processing Code                                                   |
| DE 4    | Amount, Transaction                                               |
| DE 11   | System Trace Audit Number — dipakai sebagai reference id          |
| DE 37   | Retrieval Reference Number                                        |
| DE 39   | Response Code (hanya di response; `00` = approved)                |
| DE 41   | Card Acceptor Terminal ID                                         |
| DE 102  | Account Identification 1 — dipakai sebagai destination account   |

## Yang perlu diperhatikan di kode

- **`transfer-packager.xml`** — kontrak biner: untuk tiap DE, definisikan panjang dan
  tipe encoding-nya (`IFA_NUMERIC`, `IFA_LLNUM`, dst). jPOS `GenericPackager` membaca ini
  untuk tahu cara pack/unpack tanpa kita tulis parsing manual per byte.
- **Bitmap** — DE yang tidak dipakai TIDAK dikirim sama sekali (beda dari JSON REST yang
  biasanya tetap mencantumkan field kosong/null). Bitmap-lah yang memberi tahu penerima
  DE mana saja yang perlu di-parse.
- **MTI response = MTI request + 10** — `TransferMessageBuilder.buildTransferResponse()`
  memakai `setResponseMTI()` dari jPOS untuk otomatis mengubah `0200` jadi `0210`. Ini
  konvensi standar untuk memasangkan request-response tanpa field terpisah.
- **`Iso8583Transport`** — ISO 8583 sendiri tidak mendefinisikan cara transport; di sini
  dipakai 2-byte length-prefix framing (konvensi umum di industri), mirip prinsipnya
  dengan framing di `tcp-raw-demo` tapi ukuran header berbeda.
- **`TransferClient`** mencetak hex dump pesan yang sudah di-pack — untuk menunjukkan
  bahwa ini pesan biner terstruktur, bukan teks yang mudah dibaca manusia seperti JSON.
