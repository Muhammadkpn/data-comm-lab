# data-comm-lab

Playground hands-on untuk materi **Senior SDE — Data Communication**. Semua modul memakai
skenario yang sama, **transfer dana antar rekening**, supaya yang dibandingkan benar-benar
protokol dan pola komunikasinya, bukan business logic-nya.

Prinsip yang dipegang di tiap modul (sama dengan catatan belajar):
- **Trade-off eksplisit** — tiap demo menunjukkan apa yang didapat *dan* apa yang dibayar.
- **Bisa dijalankan & dirusak sendiri** — ada skenario error / drift / anti-pattern, bukan cuma happy path.
- **Konteks Aira** — REST + JWT untuk mobile, session + CSRF untuk web, REST + Kafka antar service.

## Prasyarat

- JDK 8 atau lebih baru (CI menguji JDK 8 dan 17)
- Maven 3.6+
- `curl` untuk skenario manual

```bash
mvn verify          # compile + jalankan semua unit test
scripts/run.sh      # daftar demo yang bisa dijalankan
```

## Peta modul ↔ bab materi

| Modul | Port | Bab materi | Yang dipelajari |
|---|---|---|---|
| [`tcp-raw-demo`](tcp-raw-demo/) | 9090 | §15 TCP | Byte stream tanpa batas pesan, framing length-prefix, `readFully()` |
| [`udp-demo`](udp-demo/) | 9092 | §15 TCP vs UDP | Datagram tanpa framing, loss & reorder, retransmisi + dedupe, truncation |
| [`rest-demo`](rest-demo/) | 8080 | §4–9 REST, method, status code, versioning, pagination | v1: status code & schema drift. v2: idempotency key, PUT/PATCH/DELETE/HEAD/OPTIONS, problem+json, header versioning + deprecation, offset vs cursor, ETag/304, content negotiation |
| [`grpc-demo`](grpc-demo/) | 9091 | §12 HTTP vs gRPC | Protobuf schema-first, unary vs server streaming, drift proto |
| [`graphql-demo`](graphql-demo/) | 8081 | §13 REST vs GraphQL | Satu endpoint, field selection, N+1 vs DataLoader |
| [`auth-demo`](auth-demo/) | 8082 | §7 Auth | JWT access+refresh (rotation, revoke, `alg=none`), session + CSRF, API key, Basic, 401 vs 403 |
| [`cors-demo`](cors-demo/) | 8085 + 8086 | §11 CORS | Same-origin policy, simple vs preflight, credentials, expose headers, origin dipantulkan |
| [`realtime-demo`](realtime-demo/) | 8083 | §14 Real-time | Short polling vs long polling vs SSE vs WebSocket, diukur request & delay-nya |
| [`gateway-demo`](gateway-demo/) | 8084 | §10 Rate limiting, §16 API gateway | Token bucket vs fixed window (edge burst), 429 + `Retry-After`, auth di edge, correlation id, 502 vs 504 |
| [`protocol-comparison`](protocol-comparison/) | — | Rangkuman | Ukuran payload transfer yang sama di 6 format, diukur dengan codec asli tiap modul |
| [`iso8583-demo`](iso8583-demo/) | 9095 | Banking | Pesan biner ISO 8583 (MTI, bitmap, DE) dengan jPOS |
| [`ofs-demo`](ofs-demo/) | 9097 | Banking (core) | Format positional vs XML self-describing |

## Dokumen lab

| Dokumen | Isi |
|---|---|
| [`docs/http2-lab.md`](docs/http2-lab.md) | §3 HTTP/1.1 vs 2 vs 3: h2c Upgrade & prior knowledge, multiplexing (1 vs 20 koneksi), ALPN di TLS |
| [`docs/troubleshooting-lab.md`](docs/troubleshooting-lab.md) | §17 Diagnosis per layer: curl timing, refused vs timeout, 502 vs 504, TLS, tcpdump, DNS |
| [`docs/comparison.md`](docs/comparison.md) | Perbandingan lintas protokol: ukuran payload, kontrak, pola interaksi, kapan memilih apa |

## Urutan belajar yang disarankan

1. **Transport** — `tcp-raw-demo` lalu `udp-demo`: kenapa butuh framing, dan apa saja yang
   "gratis" dari TCP (sampai, urut, tanpa duplikat).
2. **HTTP/REST** — `rest-demo` v1 lalu v2: status code, method, idempotency, versioning,
   pagination, caching. Lanjut [`docs/http2-lab.md`](docs/http2-lab.md).
3. **Keamanan di HTTP** — `auth-demo` (siapa kamu & boleh apa) lalu `cors-demo` (apa yang
   boleh dibaca halaman lain).
4. **Alternatif kontrak** — `grpc-demo` lalu `graphql-demo`: schema-first, biner, field selection.
5. **Push ke client** — `realtime-demo`: polling vs long polling vs SSE vs WebSocket.
6. **Di depan semuanya** — `gateway-demo`: routing, auth di edge, rate limiting, 502 vs 504.
7. **Legacy banking** — `iso8583-demo` dan `ofs-demo`.
8. **Rangkuman & latihan** — [`docs/comparison.md`](docs/comparison.md) dan
   [`docs/troubleshooting-lab.md`](docs/troubleshooting-lab.md).

## Cakupan terhadap materi

| Bab | Status | Di mana |
|---|---|---|
| §2 HTTP fundamentals, idempotency, content negotiation | ✅ | `rest-demo` v2 |
| §3 HTTP/1.1 vs 2 vs 3 | ✅ HTTP/1.1 & 2 hands-on, HTTP/3 konsep | `docs/http2-lab.md` |
| §4–6 REST, method, status code | ✅ | `rest-demo` |
| §7 Auth (Basic, Bearer/JWT, API key, session) | ✅ | `auth-demo` |
| §7 OAuth 2.0 flows / OIDC / mTLS | ⚠️ Konsep + decision matrix saja | `auth-demo/README.md` |
| §8 Versioning & deprecation | ✅ | `rest-demo` v2 |
| §9 Pagination | ✅ | `rest-demo` v2 |
| §10 Rate limiting | ✅ | `gateway-demo` |
| §11 CORS & CSRF | ✅ | `cors-demo`, `auth-demo` |
| §12 gRPC | ✅ | `grpc-demo` |
| §13 GraphQL | ✅ | `graphql-demo` |
| §14 Real-time | ✅ | `realtime-demo` |
| §15 TCP vs UDP | ✅ | `tcp-raw-demo`, `udp-demo` |
| §16 API gateway / BFF | ✅ gateway, BFF konsep | `gateway-demo` |
| §17 Troubleshooting | ✅ | `docs/troubleshooting-lab.md` |
| Konteks Aira: Kafka (async antar service) | ❌ Belum ada | — |
| Konteks Aira: SOAP (core banking legacy) | ❌ Belum ada | — |

## Struktur repo

```
pom.xml                  parent Maven (Java 8, JUnit 5)
scripts/run.sh           shortcut menjalankan server/client tiap modul
scripts/gen-dev-cert.sh  sertifikat self-signed untuk lab TLS/HTTP2 (hasil di .certs/, tidak di-commit)
scripts/curl-timing.txt  format curl -w untuk breakdown waktu per fase
docs/                    lab HTTP/2, troubleshooting, perbandingan protokol
.github/workflows/       CI: mvn verify di JDK 8 & 17
<modul>/README.md        cara run, alur komunikasi, hal yang perlu diperhatikan di kode
<modul>/src/test/        unit test yang mengunci perilaku protokol tiap demo
```
