# cors-demo

Belajar **CORS & browser security** (bab 11 materi). Satu aplikasi yang listen di dua port,
supaya ada dua **origin** berbeda tanpa perlu dua domain:

| Origin | Peran |
|---|---|
| `http://localhost:8086` | "Frontend": halaman `index.html` yang dibuka di browser |
| `http://localhost:8085` | "API" yang dipanggil halaman tersebut |

Origin = **skema + host + port**. Beda port saja sudah lintas origin.

## Cara run

```bash
scripts/run.sh cors server
```
Buka **http://localhost:8086** di browser, klik kelima tombol, lalu bandingkan output halaman,
DevTools Console/Network, dan log server di terminal.

| Path API | Kebijakan |
|---|---|
| `/api/no-cors/**` | Tanpa konfigurasi CORS |
| `/api/cors/**` | CORS benar: origin eksplisit, method & header terbatas, credentials, expose headers, max-age |
| `/api/misconfig/**` | **Salah**: origin apa pun dipantulkan + `Allow-Credentials: true` |

## Hasil yang sudah diverifikasi (headless Chromium)

```
> GET http://localhost:8085/api/no-cors/balance
  TypeError: Failed to fetch
> GET http://localhost:8085/api/cors/balance
  status 200, X-Request-Id terbaca: 5840860d
> POST http://localhost:8085/api/no-cors/transfers (Content-Type: application/json)
  TypeError: Failed to fetch
> POST http://localhost:8085/api/cors/transfers (Content-Type: application/json)
  status 201, Location: /api/cors/transfers/TRF-1
> GET http://localhost:8085/api/misconfig/balance
  status 200, X-Request-Id terbaca: null

Console: Access to fetch at '.../api/no-cors/balance' from origin 'http://localhost:8086' has been blocked
         by CORS policy: No 'Access-Control-Allow-Origin' header is present on the requested resource.
```

Log server untuk klik yang sama:

```
[SERVER] GET /api/no-cors/balance Origin=http://localhost:8086 -> 200, Allow-Origin=null      <- (1) DIPROSES!
[SERVER] GET /api/cors/balance Origin=http://localhost:8086 -> 200, Allow-Origin=http://localhost:8086
[SERVER] OPTIONS /api/no-cors/transfers ... -> 403 (PREFLIGHT untuk POST)                     <- (3) POST tidak pernah dikirim
[SERVER] OPTIONS /api/cors/transfers ... -> 200 (PREFLIGHT untuk POST)
[SERVER] Transfer DIPROSES: {amount=150000}
[SERVER] POST /api/cors/transfers Origin=http://localhost:8086 -> 201
```

## Yang dipelajari

### 1. CORS melindungi data di browser user, bukan melindungi server
Tombol 1: server **menerima dan memproses** request (log 200). Browser hanya menolak
**memberikan response** ke JavaScript halaman lain. Jadi CORS **bukan** pengganti
autentikasi, dan bukan proteksi CSRF. Request "simple" (GET, atau POST form) dari situs
lain tetap sampai ke server dengan cookie user. Itu sebabnya `auth-demo` memakai token CSRF.

### 2. Simple request vs preflight

| Simple request (langsung dikirim) | Butuh preflight `OPTIONS` dulu |
|---|---|
| Method `GET`, `HEAD`, `POST` | Method lain (`PUT`, `PATCH`, `DELETE`) |
| `Content-Type`: `text/plain`, `multipart/form-data`, `application/x-www-form-urlencoded` | `Content-Type: application/json` |
| Tanpa header custom | Header custom (`Authorization`, `Idempotency-Key`, ...) |

Tombol 3: preflight ditolak, jadi **POST tidak pernah dikirim**. Data tidak berubah. Tombol 4:
preflight lolos, lalu POST dikirim.

```bash
curl -i -X OPTIONS localhost:8085/api/cors/transfers \
     -H 'Origin: http://localhost:8086' \
     -H 'Access-Control-Request-Method: POST' \
     -H 'Access-Control-Request-Headers: content-type,idempotency-key'
# HTTP/1.1 200
# Access-Control-Allow-Origin: http://localhost:8086
# Access-Control-Allow-Methods: GET,POST
# Access-Control-Allow-Headers: content-type, idempotency-key
# Access-Control-Allow-Credentials: true
# Access-Control-Max-Age: 600          <- hasil preflight di-cache 10 menit
```

Preflight menambah satu round-trip. `Access-Control-Max-Age` mengurangi biayanya, dan
inilah salah satu alasan API publik sering memilih header standar.

### 3. Header CORS lainnya
- **`Access-Control-Expose-Headers`** — tanpa ini JavaScript tidak bisa membaca header
  seperti `X-Request-Id` atau `Location` (tombol 5: `X-Request-Id terbaca: null`).
- **`Vary: Origin`** — response berbeda per origin, jadi cache/CDN harus memisahkannya.
- **Credentials** (`fetch(..., {credentials:'include'})`) hanya bekerja kalau server menjawab
  origin **persis** (bukan `*`) plus `Access-Control-Allow-Credentials: true`.

### 4. Miskonfigurasi yang sering terjadi

```bash
curl -i localhost:8085/api/misconfig/balance -H 'Origin: https://evil.example'
# Access-Control-Allow-Origin: https://evil.example
# Access-Control-Allow-Credentials: true
```

Memantulkan `Origin` apa adanya + credentials berarti **situs mana pun** bisa membaca data
user yang sedang login (saldo, profil). Ini lebih buruk daripada tanpa CORS sama sekali.
Spring sengaja menolak `allowedOrigins("*")` + `allowCredentials(true)`. `MisconfiguredCorsFilter`
mem-bypass pengaman itu, persis seperti yang sering terjadi di kode nyata.

Kesalahan lain yang serupa:
- Whitelist dengan `endsWith("bank.co.id")` — lolos untuk `evilbank.co.id`.
- Mengizinkan origin `null` (dipakai iframe sandbox & file lokal).
- Mengizinkan semua subdomain padahal ada subdomain yang bisa diisi konten user.

### 5. Same-origin policy, CORS, CSRF — hubungannya

| | Melindungi dari | Ditegakkan oleh |
|---|---|---|
| Same-origin policy | Situs lain **membaca** data kita | Browser |
| CORS | (Melonggarkan SOP secara terkontrol) | Browser, berdasar header server |
| CSRF token / `SameSite` cookie | Situs lain **mengirim** request atas nama user | Server / browser |

## Yang perlu diperhatikan di kode

- **`CorsDemoApplication`** — connector Tomcat tambahan untuk port 8086.
- **`CorsConfig`** — kebijakan CORS yang benar lewat `addCorsMappings`.
- **`MisconfiguredCorsFilter`** — contoh yang SALAH.
- **`RequestLogFilter`** — membuktikan request lintas origin tetap sampai ke server.
- **`CorsDemoTest`** — preflight, origin ditolak (403), dan miskonfigurasi.
