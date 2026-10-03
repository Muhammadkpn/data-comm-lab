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
| [`rest-demo`](rest-demo/) | 8080 | §4–9 REST, method, status code, versioning, pagination | v1: status code & schema drift. v2: idempotency key, PUT/PATCH/DELETE/HEAD/OPTIONS, problem+json, header versioning + deprecation, offset vs cursor, ETag/304, content negotiation |
| [`grpc-demo`](grpc-demo/) | 9091 | §12 HTTP vs gRPC | Protobuf schema-first, unary vs server streaming, drift proto |
| [`graphql-demo`](graphql-demo/) | 8081 | §13 REST vs GraphQL | Satu endpoint, field selection, N+1 vs DataLoader |
| [`auth-demo`](auth-demo/) | 8082 | §7 Auth | JWT access+refresh (rotation, revoke, `alg=none`), session + CSRF, API key, Basic, 401 vs 403 |
| [`realtime-demo`](realtime-demo/) | 8083 | §14 Real-time | Short polling vs long polling vs SSE vs WebSocket, diukur request & delay-nya |
| [`iso8583-demo`](iso8583-demo/) | 9095 | Banking | Pesan biner ISO 8583 (MTI, bitmap, DE) dengan jPOS |
| [`ofs-demo`](ofs-demo/) | 9097 | Banking (core) | Format positional vs XML self-describing |

## Urutan belajar yang disarankan

1. **Transport dulu** — `tcp-raw-demo`: rasakan sendiri kenapa butuh framing.
2. **HTTP/REST** — `rest-demo`: apa yang "gratis" dari HTTP dibanding TCP mentah.
3. **Alternatif kontrak** — `grpc-demo` lalu `graphql-demo`: schema-first, binary, field selection.
4. **Legacy banking** — `iso8583-demo` dan `ofs-demo`: format yang masih hidup di integrasi core/switching.

## Struktur repo

```
pom.xml                  parent Maven (Java 8, JUnit 5)
scripts/run.sh           shortcut menjalankan server/client tiap modul
.github/workflows/       CI: mvn verify di JDK 8 & 17
<modul>/README.md        cara run, alur komunikasi, hal yang perlu diperhatikan di kode
<modul>/src/test/        unit test yang mengunci perilaku protokol tiap demo
```
