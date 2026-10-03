# Lab: network troubleshooting (bab 17)

Latihan mendiagnosis masalah komunikasi memakai demo di repo ini sebagai "production" mini.
Setiap skenario punya gejala yang bisa dipicu sendiri, jadi alurnya bisa dilatih sampai
otomatis: **gejala → layer → alat → bukti**.

> Output yang ditampilkan di bawah sudah diverifikasi (curl, nc, lsof, openssl) di Linux.
> `dig`, `ss`, `tcpdump`, dan `traceroute` tidak tersedia di environment verifikasi, jadi
> untuk alat itu hanya perintahnya yang dicantumkan. Jalankan sendiri di laptop.

## Pendekatan: tentukan layer dulu

```
DNS ──► TCP connect ──► TLS handshake ──► HTTP request ──► aplikasi ──► dependency (DB, core, service lain)
 dig       nc / ss        openssl s_client    curl -v          log/APM       APM span
```

Pertanyaan pertama selalu: **request berhenti di layer mana?** Satu perintah menjawab
sebagian besar pertanyaan itu sekaligus:

```bash
curl -s -o /dev/null -w "@scripts/curl-timing.txt" <url>
```

[`scripts/curl-timing.txt`](../scripts/curl-timing.txt) memecah waktu per fase. Angka di
curl bersifat **kumulatif** sejak awal request.

```
     dns_lookup:  0.000019s    <- resolve nama
    tcp_connect:  0.000325s    <- TCP 3-way handshake selesai
  tls_handshake:  0.018836s    <- TLS selesai (0 kalau http://)
 request_sent  :  0.018974s
           ttfb:  0.025576s    <- byte pertama response = waktu proses server
          total:  0.026557s
   http_version:  2
         status:  200
```

(Contoh di atas: `https://localhost:8443` profile `tls` rest-demo. TLS handshake ~18 ms
mendominasi request pertama. Itu biaya yang dihindari connection reuse / keep-alive.)

## Skenario 1 — "API lambat": server atau jaringan?

```bash
scripts/run.sh rest server      # 8080
scripts/run.sh gateway server   # 8084

curl -s -o /dev/null -w "@scripts/curl-timing.txt" localhost:8080/v2/accounts/1010001
curl -s -o /dev/null -w "@scripts/curl-timing.txt" "localhost:8084/api/slow?ms=1200" -H 'X-API-Key: key-partner'
```

```
== cepat                          == lambat (lewat gateway)
    tcp_connect:  0.000195s           tcp_connect:  0.000114s
           ttfb:  0.005572s                  ttfb:  1.284477s
          total:  0.006129s                 total:  1.284633s
```

**Bacaan:** `tcp_connect` kecil di keduanya, jadi jaringan sehat. Selisih ada di
`ttfb − request_sent`, artinya server yang lambat. Langkah berikutnya: buka APM / log dengan
`X-Request-Id` dari response gateway (`AccessLogFilter` mencatat `... -> 200 (1284 ms)`),
lalu cari span terlama.

Kalau sebaliknya `tcp_connect` atau `dns_lookup` yang besar, masalahnya di jaringan/DNS dan
log aplikasi tidak akan menunjukkan apa pun.

## Skenario 2 — "Tidak bisa connect": 502, refused, atau timeout?

```bash
nc -zv localhost 8080      # Connection to localhost (127.0.0.1) 8080 port [tcp/http-alt] succeeded!
nc -zv -w1 localhost 8099  # nc: connect to localhost (127.0.0.1) port 8099 (tcp) failed: Connection refused
curl -sS localhost:8099    # curl: (7) Failed to connect to localhost port 8099 after 0 ms: Couldn't connect to server
```

| Gejala | Artinya | Biasanya |
|---|---|---|
| `Connection refused` **cepat** | Host hidup, port tidak ada yang listen (RST) | Service mati / salah port |
| Connect **menggantung** lalu timeout | Paket dibuang di tengah jalan | Firewall / security group / routing |
| `502` dari gateway | Gateway tidak bisa connect ke upstream | Upstream mati (lihat `curl -i localhost:8084/api/down`) |
| `504` dari gateway | Upstream terhubung tapi tidak menjawab | Upstream lambat (lihat `/api/slow?ms=3000`) |

Port apa yang benar-benar listen di mesin ini?

```bash
lsof -nP -iTCP -sTCP:LISTEN | grep java
# java  6930 root  37u  IPv4 ...  TCP *:8084 (LISTEN)
# java  6950 root  38u  IPv4 ...  TCP *:8080 (LISTEN)

ss -ltnp                   # alternatif modern di Linux
ss -tn state established   # koneksi aktif; banyak TIME_WAIT/CLOSE_WAIT = petunjuk connection leak
```

## Skenario 3 — TLS

```bash
scripts/gen-dev-cert.sh
mvn -pl rest-demo spring-boot:run -Dspring-boot.run.profiles=tls     # 8443
```

| Perintah | Hasil | Diagnosis |
|---|---|---|
| `curl https://localhost:8443/v2/accounts/1010001` | `curl: (60) SSL certificate problem: self-signed certificate` | Client tidak percaya CA penerbit. Pasang CA di truststore (`--cacert`), **jangan** `-k` di production |
| `curl --cacert .certs/dev-cert.pem --resolve wrong.example:8443:127.0.0.1 https://wrong.example:8443/...` | `curl: (60) SSL: no alternative certificate subject name matches target host name 'wrong.example'` | Hostname tidak ada di SAN sertifikat |
| `curl --cacert .certs/dev-cert.pem https://localhost:8443/...` | `200`, `HTTP/2` | Sehat |

Lihat isi sertifikat yang **benar-benar** dikirim server (bukan yang Anda kira terpasang):

```bash
openssl s_client -connect localhost:8443 -servername localhost </dev/null 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates
# subject=O = data-comm-lab, CN = localhost
# issuer=O = data-comm-lab, CN = localhost
# notBefore=Oct  3 05:06:00 2026 GMT
# notAfter=Oct  3 05:06:00 2027 GMT
```

Insiden TLS paling sering di production: **sertifikat kedaluwarsa** (pantau `notAfter`),
**intermediate certificate tidak dikirim** (`openssl s_client -showcerts`, rantai harus
lengkap), dan **SNI salah** (`-servername` berbeda dari host).

## Skenario 4 — Error di level HTTP / aplikasi

Gunakan `curl -i` (header + body), lalu baca status code-nya:

| Status | Lihat ke mana |
|---|---|
| `400` / `422` problem+json | Body `detail` & `errors`. Masalah di request client (`rest-demo` v2) |
| `401` + `WWW-Authenticate` | Token tidak ada/kedaluwarsa/dicabut, alasannya di header (`auth-demo`) |
| `403` | Identitas valid, hak kurang (`insufficient_scope`) atau CSRF/CORS ditolak |
| `404` dari gateway `no-route` vs dari service | Bedakan lewat body: `problems/no-route` berarti routing gateway |
| `409` / `429` | Konflik idempotency / rate limit, ikuti `Retry-After` |
| `5xx` | `traceId` / `X-Request-Id`, lalu log & APM |

Dan di browser, **error CORS hanya terlihat di console browser**. Server mencatat
request-nya sebagai sukses (`cors-demo`, `RequestLogFilter`).

## Skenario 5 — Melihat byte di kabel

Untuk protokol biner atau saat curiga ada salah framing (`tcp-raw-demo`, `iso8583-demo`,
`grpc-demo`), lihat paket sesungguhnya:

```bash
# Linux: loopback = lo, macOS: lo0. Butuh sudo.
sudo tcpdump -i lo -nn -X 'tcp port 9090'     # tcp-raw-demo: terlihat 4 byte length prefix lalu payload
sudo tcpdump -i lo -nn -X 'tcp port 9095'     # iso8583-demo: MTI "0200", bitmap, data element
sudo tcpdump -i lo -nn -X 'udp port 9092'     # udp-demo: satu datagram per pesan
sudo tcpdump -i lo -w grpc.pcap 'tcp port 9091'   # buka di Wireshark: Decode As -> HTTP2, lalu protobuf
```

Hal yang dicari: TCP handshake (`SYN`, `SYN-ACK`, `ACK`), retransmisi, `RST` (koneksi
ditolak/diputus), ukuran segmen, dan apakah byte yang dikirim sesuai format yang disepakati.

## Skenario 6 — DNS

```bash
dig api.example.com +short          # A record
dig api.example.com CNAME           # rantai alias
dig api.example.com +trace          # resolusi dari root
dig @8.8.8.8 api.example.com        # bandingkan resolver internal vs publik
```

Gejala khas DNS: `dns_lookup` besar di curl-timing, `Could not resolve host`, atau hanya
sebagian instance yang gagal (cache/TTL berbeda). Setelah ganti IP, client masih ke alamat
lama sampai **TTL** habis, dan JVM punya cache DNS sendiri (`networkaddress.cache.ttl`).

## Latihan

1. Jalankan `rest-demo` + `gateway-demo`, panggil `/api/slow?ms=2500`. Kenapa 504, padahal
   upstream akhirnya menjawab? Ubah `gateway.read-timeout-ms` dan ulangi.
2. Matikan `rest-demo`, panggil `/api/accounts/1010001` lewat gateway. Status apa? Bedanya
   dengan latihan 1?
3. Buat sertifikat yang sudah kedaluwarsa (`keytool -genkeypair ... -startdate -3d -validity 1`),
   jalankan profile `tls` dengan keystore itu, lalu baca pesan error curl dan output
   `openssl x509 -dates`.
4. Rekam `tcpdump` saat `tcp-raw-demo` `BadFramingDemo` berjalan. Temukan di dump di mana
   batas pesan yang sebenarnya, dan di mana reader yang salah memotongnya.
5. Ambil satu `X-Request-Id` dari response gateway, lalu temukan baris log yang sama di
   gateway dan di service upstream. Itulah inti distributed tracing.
