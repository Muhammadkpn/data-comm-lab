# ofs-demo

Belajar **OFS** (Oracle FLEXCUBE messaging format, umum di core banking) dengan skenario
yang sama: transfer dana antar rekening. Fokus: perbedaan native format vs XML format,
ditransport langsung lewat raw TCP (skip MQ, disederhanakan untuk demo).

## Cara run

Butuh 2 terminal.

**Terminal 1 — jalankan server:**
```bash
mvn -pl ofs-demo compile exec:java -Dexec.mainClass="com.learn.datacomm.ofs.TransferServer"
```

**Terminal 2 — jalankan client:**
```bash
mvn -pl ofs-demo compile exec:java -Dexec.mainClass="com.learn.datacomm.ofs.TransferClient"
```

Client mengirim transfer yang sama dua kali: sekali dalam format native, sekali dalam
format XML — supaya perbedaan ukuran dan strukturnya kelihatan langsung di output.

## Alur komunikasi

```
CLIENT                                                    SERVER
  |--- payload teks (native ATAU XML) + delimiter ETX --->|
  |                                                        |  deteksi format dari isi
  |                                                        |  payload (native vs XML),
  |                                                        |  lalu decode sesuai format
  |<-- payload response (format sama) + delimiter ETX -----|
```

Catatan: di produksi FLEXCUBE, OFS biasanya ditransport lewat MQ Series, bukan TCP
langsung. Modul ini sengaja skip MQ supaya fokus ke perbedaan FORMAT pesannya dulu.

## Perbandingan format

**Native** — pipe-delimited header + body dalam kurung kurawal, field POSISIONAL:
```
HEADER,IN,FTXFRQ{1010001,2020002,150000,REF-OFS-0001}
```

**XML** — self-describing, field diidentifikasi lewat nama tag:
```xml
<OFS_MESSAGE>
  <HEADER><FUNCTION_ID>FTXFRQ</FUNCTION_ID><TYPE>IN</TYPE></HEADER>
  <BODY>
    <SOURCE_ACCOUNT>1010001</SOURCE_ACCOUNT>
    <DESTINATION_ACCOUNT>2020002</DESTINATION_ACCOUNT>
    <AMOUNT>150000</AMOUNT>
    <REFERENCE_ID>REF-OFS-0001</REFERENCE_ID>
  </BODY>
</OFS_MESSAGE>
```

## Yang perlu diperhatikan di kode

- **`NativeFormatCodec`** — parsing cukup `String.split(",")` karena formatnya
  positional, tapi TIDAK self-describing: kalau tidak tahu urutan field yang benar untuk
  function id tertentu, pesan ini tidak bisa diartikan hanya dari melihat teksnya.
- **`XmlFormatCodec`** — dibangun/diparsing manual pakai `StringBuilder` dan
  `javax.xml.parsers` bawaan JDK (bukan JAXB) supaya mekanismenya kelihatan jelas.
  Field self-describing lewat nama tag, lebih toleran terhadap perubahan urutan field.
- **`OfsTransport`** — dibanding length-prefix framing di `tcp-raw-demo`/`iso8583-demo`,
  di sini dipakai **delimiter-based framing** (diakhiri karakter ETX). Trade-off-nya
  didiskusikan di komentar kode: gampang di-debug sebagai teks, tapi delimiter harus
  dipastikan tidak pernah muncul di payload asli.
- **`TransferServer`** mendeteksi format dari isi payload (`<` di awal = XML) — ini
  menunjukkan bahwa parser harus tahu di luar protokol bagaimana cara membedakan kedua
  format ini, karena OFS sendiri tidak punya field "format type" yang seragam.
- Bandingkan panjang karakter kedua format di output client — XML jauh lebih verbose
  untuk data yang identik.
