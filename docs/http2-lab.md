# Lab: HTTP/1.1 vs HTTP/2 (bab 3)

`rest-demo` sekarang melayani **HTTP/2** di dua mode:

| Mode | Port | Cara aktif | Dipakai oleh |
|---|---|---|---|
| **h2c** (HTTP/2 tanpa TLS) | 8080 | `server.http2.enabled=true` (default) | Service-to-service internal, gRPC tanpa TLS |
| **h2** (HTTP/2 di atas TLS) | 8443 | profile `tls` | Browser (browser **hanya** mau HTTP/2 lewat TLS) |

Client HTTP/1.1 biasa tetap dilayani seperti sebelumnya. HTTP/2 hanya dipakai kalau client
memintanya.

## 1. h2c — dua cara memulai

```bash
scripts/run.sh rest server
```

**Upgrade** dari HTTP/1.1 (client belum tahu server mendukung HTTP/2):

```bash
curl -sv --http2 -o /dev/null localhost:8080/v2/accounts/1010001
# > GET /v2/accounts/1010001 HTTP/1.1
# > Connection: Upgrade, HTTP2-Settings
# > Upgrade: h2c
# > HTTP2-Settings: AAMAAABkAAQAoAAAAAIAAAAA
# < HTTP/1.1 101
# < Connection: Upgrade
#   ... sesudahnya koneksi yang sama bicara HTTP/2
```

Mekanismenya sama dengan WebSocket (`realtime-demo`): request HTTP/1.1 biasa,
`101 Switching Protocols`, lalu protokol berganti di koneksi TCP yang sama.

**Prior knowledge** (client sudah tahu, jadi langsung HTTP/2):

```bash
curl -sv --http2-prior-knowledge -o /dev/null localhost:8080/v2/accounts/1010001
# * [HTTP/2] [1] OPENED stream for http://localhost:8080/v2/accounts/1010001
# * [HTTP/2] [1] [:method: GET]
# * [HTTP/2] [1] [:scheme: http]
# * [HTTP/2] [1] [:authority: localhost:8080]
# * [HTTP/2] [1] [:path: /v2/accounts/1010001]
```

Di HTTP/2, request line menjadi **pseudo-header** (`:method`, `:path`, ...) dan setiap
request adalah **stream** bernomor (`[1]`) di dalam satu koneksi.

## 2. Multiplexing — satu koneksi untuk banyak request

20 request paralel ke endpoint yang sama:

```bash
A=""; for i in $(seq 1 20); do A="$A -o /dev/null localhost:8080/v2/accounts/1010001"; done
curl -s -Z --parallel-max 20 --http2-prior-knowledge -w '%{num_connects}\n' $A | paste -sd+ | bc   # -> 1
curl -s -Z --parallel-max 20 --http1.1              -w '%{num_connects}\n' $A | paste -sd+ | bc   # -> 20
```

Hasil yang sudah diverifikasi:

```
HTTP/2   total koneksi TCP baru: 1
HTTP/1.1 total koneksi TCP baru: 20
```

HTTP/1.1 hanya bisa satu request aktif per koneksi. Untuk paralel, client membuka banyak
koneksi (browser membatasi ~6 per host), dan masing-masing membayar TCP handshake +
TLS handshake + slow start. HTTP/2 memecah setiap request/response menjadi **frame** yang
diselang-seling di satu koneksi.

## 3. h2 di atas TLS — ALPN

```bash
scripts/gen-dev-cert.sh                                             # buat .certs/ (di-gitignore)
mvn -pl rest-demo spring-boot:run -Dspring-boot.run.profiles=tls    # port 8443

curl -sv --cacert .certs/dev-cert.pem -o /dev/null https://localhost:8443/v2/accounts/1010001
# * ALPN: curl offers h2,http/1.1
# * SSL connection using TLSv1.3 / TLS_AES_256_GCM_SHA384 / X25519 / RSASSA-PSS
# * ALPN: server accepted h2
# < HTTP/2 200
```

**ALPN** (Application-Layer Protocol Negotiation): client menawarkan daftar protokol
**di dalam TLS handshake**, server memilih satu. Tidak ada round-trip tambahan seperti
Upgrade h2c.

```bash
openssl s_client -connect localhost:8443 -alpn h2 -servername localhost -CAfile .certs/dev-cert.pem </dev/null
# New, TLSv1.3, Cipher is TLS_AES_256_GCM_SHA384
# ALPN protocol: h2
# Verify return code: 0 (ok)
```

## Ringkasan HTTP/1.1 vs HTTP/2 vs HTTP/3

| | HTTP/1.1 | HTTP/2 | HTTP/3 |
|---|---|---|---|
| Format | Teks | Biner (frame) | Biner (frame) |
| Transport | TCP | TCP | **QUIC di atas UDP** |
| Request paralel | Banyak koneksi | **Multiplexing** stream di 1 koneksi | Multiplexing, stream independen |
| Header | Teks berulang di setiap request | **HPACK** (kompresi + tabel) | QPACK |
| Head-of-line blocking | Level HTTP (1 request per koneksi) | Hilang di level HTTP, **masih ada di level TCP** | Hilang (loss di satu stream tidak menahan stream lain) |
| Setup koneksi baru | TCP + TLS (2–3 RTT) | TCP + TLS | 1 RTT, 0-RTT untuk resume |
| Ganti jaringan (Wi-Fi → 4G) | Koneksi putus | Koneksi putus | **Connection migration** (connection ID) |

**HOL blocking di TCP** adalah alasan HTTP/3 pindah ke UDP. Kalau satu segmen TCP hilang,
kernel menahan **semua** byte sesudahnya sampai retransmisi tiba, termasuk byte milik stream
HTTP/2 lain yang sebenarnya sudah lengkap. QUIC membangun keandalan per stream di user
space (konsepnya terlihat di `udp-demo`: retransmisi & dedupe dibangun sendiri di atas UDP).

**Kapan HTTP/2 / HTTP/3 penting?**
- Mobile app dengan banyak request kecil di jaringan latensi tinggi → manfaat terbesar.
- gRPC **mewajibkan** HTTP/2 (`grpc-demo`), karena streaming butuh multiplexing.
- Antar-service di data center (latensi rendah) → manfaatnya lebih kecil.
- Konteks Aira: HTTP/1.1 dominan, beberapa titik HTTP/2. HTTP/3 biasanya diaktifkan di
  CDN/edge, bukan di aplikasi.
