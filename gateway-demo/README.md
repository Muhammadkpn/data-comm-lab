# gateway-demo

Belajar **API Gateway** (bab 16) dan **rate limiting** (bab 10) dengan gateway mini yang
ditulis manual. Setiap tanggung jawab gateway adalah satu class kecil, jadi alurnya bisa
diikuti dari atas ke bawah:

```
client ──► CorrelationIdFilter ──► AccessLogFilter ──► ApiKeyAuthFilter ──► RateLimitFilter ──► ProxyController ──► upstream
           X-Request-Id             1 baris log         401 di edge          429 + Retry-After    routing, 502/504
```

Di production tugas ini dikerjakan produk jadi seperti Kong, Spring Cloud Gateway, APISIX,
Envoy, atau AWS API Gateway. Konsepnya sama.

## Cara run

```bash
scripts/run.sh gateway server     # port 8084
scripts/run.sh gateway burst      # di terminal lain: demo algoritma rate limit
```

Gateway punya upstream tiruan bawaan (`/api/echo`, `/api/slow`, `/api/down`), jadi bisa
dicoba tanpa service lain. Kalau `rest-demo` (8080) dan `realtime-demo` (8083) juga
dijalankan, route sungguhan ikut aktif:

| Route publik | Upstream |
|---|---|
| `/api/transfers/**` | `http://localhost:8080/v2/transfers/**` |
| `/api/accounts/**` | `http://localhost:8080/v2/accounts/**` |
| `/api/realtime/**` | `http://localhost:8083/transfers/**` |
| `/api/echo/**` | upstream tiruan: memantulkan header yang diterima |
| `/api/slow?ms=` | upstream tiruan: lambat (demo 504) |
| `/api/down` | port yang tidak ada (demo 502) |

API key: `key-mobile` (client `mobile-app`, token bucket) dan `key-partner` (client
`partner-x`, fixed window).

## 1. Rate limiting — token bucket vs fixed window

Hasil `scripts/run.sh gateway burst` yang sudah diverifikasi:

```
=== A. Token bucket: 10 request sekaligus ===
[CLIENT]   200 200 200 200 200 429 429 429 429 429  sisa=0  (Retry-After: 1s)
[CLIENT] ... tunggu 2 detik (bucket terisi 2 token)
[CLIENT]   200 200 429  sisa=0  (Retry-After: 1s)

=== B. Fixed window: burst di TEPI jendela ===
[CLIENT] 400 ms SEBELUM jendela berganti:
[CLIENT]   200 200 200 200 200  sisa=0
[CLIENT] 50 ms SESUDAH jendela berganti:
[CLIENT]   200 200 200 200 200  sisa=0
[CLIENT] lolos total 10/10 dalam < 1 detik  (limit: 5 per 5 detik)

=== C. Token bucket dengan pola yang sama ===
[CLIENT]   200 200 200 200 200  sisa=0
[CLIENT]   429 429 429 429 429  sisa=0  (Retry-After: 1s)
[CLIENT] lolos total 5/10
```

| Algoritma | Cara kerja | Burst | Kelemahan |
|---|---|---|---|
| Fixed window | Hitung per jendela tetap, reset di awal jendela | Sampai **2× limit** di tepi jendela | Edge burst (skenario B) |
| Sliding window | Hitung dalam jendela yang bergeser | Akurat | Lebih mahal (log/counter per sub-jendela) |
| **Token bucket** | Token terisi `r`/detik sampai kapasitas `b` | Sampai `b`, lalu rata-rata `r` | Dua parameter yang perlu di-tune |
| Leaky bucket | Antrean keluar dengan laju tetap | Tidak ada (output halus) | Request menunggu / dibuang |

Yang perlu diperhatikan:
- **Per client, bukan per IP.** Ribuan user mobile bisa berbagi satu IP publik operator (NAT).
  Per-IP hanya untuk traffic anonim.
- **Komunikasikan kuota** di setiap response: `X-RateLimit-Limit`, `X-RateLimit-Remaining`,
  `X-RateLimit-Reset`. Saat ditolak: `429` + `Retry-After`.
- **Terdistribusi:** gateway biasanya banyak instance. State limiter disimpan di Redis dan
  diperbarui atomik (Lua script / `INCR`+`EXPIRE`), kalau tidak tiap instance punya kuota sendiri.
- **Rate limiting vs throttling:** rate limiting *menolak* (429); throttling *memperlambat*
  (antrean/delay) supaya backend tidak kewalahan.

## 2. Routing, header, dan error yang dibuat gateway

```bash
curl -i 'localhost:8084/api/echo/hello?x=1' -H 'X-API-Key: key-partner' -H 'X-Request-Id: demo-123'
```
```
HTTP/1.1 200
X-Request-Id: demo-123
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 4
X-RateLimit-Reset: 2

{"upstreamSaw":"GET /_mock/echo/hello?x=1",
 "headers":{"x-client-id":"partner-x","x-forwarded-for":"127.0.0.1","x-forwarded-host":"localhost:8084",
            "x-forwarded-proto":"http","x-request-id":"demo-123", ...}}
```

Dari sisi upstream terlihat bahwa:
- **`X-API-Key` dibuang** — kredensial client tidak bocor ke service internal.
- **`X-Client-Id` ditambahkan** — upstream cukup percaya identitas dari gateway, *dengan
  syarat* upstream tidak bisa diakses langsung dari luar (network policy / mTLS).
- **`X-Request-Id`** diteruskan dan dikembalikan ke client. Satu id untuk mencari di log
  gateway, log service, dan APM.
- **`X-Forwarded-*`** — upstream tetap tahu IP & host asli client.
- **Hop-by-hop header** (`Connection`, `Transfer-Encoding`, ...) tidak diteruskan (RFC 7230).
- **`Location` ditulis ulang** — upstream menjawab `/v2/transfers/TRF-0001`, gateway
  mengembalikan `/api/transfers/TRF-0001` supaya path internal tidak bocor.

Error yang **dibuat gateway sendiri** (semua `application/problem+json`):

```bash
curl -i localhost:8084/api/echo/hello                                    # 401 -- tidak sampai ke upstream
curl -i 'localhost:8084/api/slow?ms=3000' -H 'X-API-Key: key-partner'    # 504 Gateway Timeout (read-timeout 2 s)
curl -i localhost:8084/api/down -H 'X-API-Key: key-partner'              # 502 Bad Gateway
curl -i localhost:8084/api/unknown -H 'X-API-Key: key-partner'           # 404 no-route
```

**502 vs 504** sangat berguna saat on-call: 502 = upstream mati / menolak koneksi; 504 =
upstream hidup tapi lambat (cek query DB, dependency downstream, thread pool).

Access log gateway, satu baris per request untuk semua service:

```
[GATEWAY] id=demo-123 client=partner-x GET /api/echo/hello -> 200 (6 ms)
[GATEWAY] id=c229f51d-e74 client=null GET /api/echo/hello -> 401 (0 ms)
[GATEWAY] id=a145e987-f23 client=partner-x GET /api/slow -> 504 (2004 ms)
[GATEWAY] id=73dc40a3-916 client=partner-x GET /api/down -> 502 (3 ms)
[GATEWAY] id=b129e86c-365 client=partner-x POST /api/transfers -> 201 (84 ms)
```

## Trade-off API gateway

| Pro | Kontra |
|---|---|
| Client cukup kenal satu host & satu kontrak | Satu titik gagal, jadi wajib HA (multi instance + health check) |
| Auth, rate limit, logging, TLS di satu tempat | Tambahan hop dan latensi di setiap request |
| Service internal bebas dipindah/dipecah | Risiko "god service" kalau business logic ikut masuk |
| Observability seragam (error rate & latency per route) | Konfigurasi & deploy gateway jadi critical path |

**BFF (Backend for Frontend):** gateway khusus per jenis client (mis. satu untuk mobile,
satu untuk web) yang juga boleh mengagregasi beberapa panggilan menjadi satu response
yang pas untuk layar tersebut. Kadang diimplementasikan dengan GraphQL (`graphql-demo`).

Keterbatasan proxy di demo ini (sengaja disederhanakan): body di-buffer penuh, jadi
**SSE dan WebSocket tidak bisa lewat** (`/api/realtime/.../events` baru terkirim setelah
stream selesai). Gateway sungguhan harus mendukung streaming dan `Upgrade`, serta
mematikan response buffering untuk `text/event-stream`.

## Yang perlu diperhatikan di kode

- **`GatewayConfig`** — urutan filter (`FilterRegistrationBean.setOrder`), API key → client,
  dan limiter per client.
- **`TokenBucketRateLimiter` / `FixedWindowRateLimiter`** — keduanya menerima jam yang bisa
  diganti, jadi bisa diuji tanpa `sleep` (`RateLimiterTest`).
- **`ProxyController`** — pencocokan prefix terpanjang, filter header, mapping exception →
  502/504, rewrite `Location`.
- **`GatewayIntegrationTest`** — gateway sungguhan di depan upstream palsu (JDK `HttpServer`).
