# auth-demo

Belajar **authentication & authorization** (bab 7 materi) dengan empat pola yang berjalan
berdampingan di satu server, persis seperti backend yang melayani beberapa jenis client:

| Path | Mekanisme | Client nyata (konteks Aira) |
|---|---|---|
| `/mobile/**` | Bearer **JWT** (access + refresh), stateless | Mobile app |
| `/web/**` | **Session cookie + CSRF** | EBizbanking di browser |
| `/partner/**` | **API key** di header | Integrasi partner server-to-server |
| `/internal/**` | **HTTP Basic** | Tooling internal |
| `/naive/**` | JWT dengan verifier **rentan** `alg=none` | Contoh yang SALAH |

## Cara run

```bash
scripts/run.sh auth server        # atau: mvn -pl auth-demo spring-boot:run
```
Server jalan di `http://localhost:8082`.

User demo:

| Username | Password | Hak |
|---|---|---|
| `andi` | `andi123` | scope `accounts:read transfers:write` |
| `budi` | `budi123` | scope `accounts:read` saja (untuk demo 403) |
| `ops` | `ops-secret` | role `OPS` untuk `/internal/**` |

API key partner: `pk_test_mitra_ewallet_001`.

## 1. Mobile — JWT access + refresh token

```bash
# Login -> dapat access token (umur 60 detik) + refresh token
curl -s -X POST localhost:8082/mobile/auth/login -H 'Content-Type: application/json' \
     -d '{"username":"andi","password":"andi123"}'
# {"tokenType":"Bearer","accessToken":"eyJhbGciOiJIUzI1NiIs...","expiresIn":60,"refreshToken":"...","scope":"accounts:read transfers:write"}

ACCESS=<accessToken>
curl -i localhost:8082/mobile/accounts/me -H "Authorization: Bearer $ACCESS"     # 200
```

**Payload JWT hanya di-encode, bukan dienkripsi.** Decode bagian tengahnya:

```bash
echo $ACCESS | cut -d. -f2 | base64 -d 2>/dev/null; echo
# {"sub":"andi","scope":"accounts:read transfers:write","iat":1791003009,"exp":1791003069,"jti":"2814d835-..."}
```

Siapa pun yang memegang token bisa membaca isinya. Jangan taruh data sensitif di sana.

### Yang bisa dicoba

| Skenario | Hasil | Pelajaran |
|---|---|---|
| Tanpa header `Authorization` | **401** `WWW-Authenticate: Bearer realm="mobile"` | 401 = "siapa kamu?" |
| Ubah 1 karakter token | **401** `error="invalid_token", error_description="Signature tidak valid"` | Signature menjamin integritas |
| Tunggu > 60 detik | **401** `Token kedaluwarsa` | Saatnya pakai refresh token |
| Login `budi`, lalu `POST /mobile/transfers` | **403** `error="insufficient_scope"` | 403 = "aku kenal kamu, tapi tidak boleh". Refresh tidak akan membantu |
| `POST /mobile/logout` lalu pakai token yang sama | **401** `Token sudah dicabut` | Logout JWT butuh state (denylist `jti`) |

### Refresh token rotation + reuse detection

```bash
curl -s -X POST localhost:8082/mobile/auth/refresh -H 'Content-Type: application/json' \
     -d '{"refreshToken":"<refresh-1>"}'      # 200, dapat <refresh-2>
curl -s -X POST localhost:8082/mobile/auth/refresh -H 'Content-Type: application/json' \
     -d '{"refreshToken":"<refresh-1>"}'      # 401 -- refresh-1 dipakai ULANG
curl -s -X POST localhost:8082/mobile/auth/refresh -H 'Content-Type: application/json' \
     -d '{"refreshToken":"<refresh-2>"}'      # 401 -- seluruh sesi ikut dicabut
```

Setiap refresh memberi refresh token baru. Kalau token lama dipakai lagi, berarti ada **dua
pihak** yang memegangnya (kemungkinan dicuri). Server tidak tahu yang mana yang asli, jadi
seluruh "family" dicabut dan keduanya harus login ulang.

### Serangan `alg=none`

```bash
H=$(printf '{"alg":"none","typ":"JWT"}' | base64 | tr '+/' '-_' | tr -d '=')
P=$(printf '{"sub":"andi","scope":"accounts:read transfers:write","exp":9999999999,"jti":"x"}' | base64 | tr '+/' '-_' | tr -d '=')
curl -i localhost:8082/naive/accounts/me  -H "Authorization: Bearer $H.$P."   # 200 -- token PALSU diterima!
curl -i localhost:8082/mobile/accounts/me -H "Authorization: Bearer $H.$P."   # 401 -- algoritma ditolak
```

`NaiveJwtVerifier` percaya field `alg` di header token. `JwtService` yang benar
**menentukan sendiri** algoritma yang diterima (`HS256`), lalu membandingkan signature
secara constant-time (`MessageDigest.isEqual`).

> JWT di modul ini ditulis manual supaya strukturnya kelihatan. Di production pakai library
> yang sudah diaudit (Nimbus JOSE / jjwt / `spring-boot-starter-oauth2-resource-server`).

## 2. Web — session cookie + CSRF

```bash
J=/tmp/cookies.txt
curl -s -c $J -b $J -X POST localhost:8082/web/login -H 'Content-Type: application/json' \
     -d '{"username":"andi","password":"andi123"}'                     # dapat JSESSIONID
curl -s -c $J -b $J localhost:8082/web/accounts/me                      # 200, cukup cookie
curl -i -c $J -b $J -X POST localhost:8082/web/transfers \
     -H 'Content-Type: application/json' -d '{"amount":1}'              # 403 -- tanpa token CSRF
curl -s -c $J -b $J localhost:8082/web/csrf                             # {"headerName":"X-XSRF-TOKEN","token":"..."}
curl -i -c $J -b $J -X POST localhost:8082/web/transfers -H "X-XSRF-TOKEN: <token>" \
     -H 'Content-Type: application/json' -d '{"amount":1}'              # 201
```

**Kenapa web butuh CSRF tapi mobile tidak?** Browser **otomatis** melampirkan cookie ke
setiap request ke domain kita, termasuk request yang dipicu form di situs penyerang. Jadi
cookie saja tidak membuktikan bahwa *halaman kita* yang mengirim request. Token CSRF
(cookie `XSRF-TOKEN` → header `X-XSRF-TOKEN`) hanya bisa dibaca JavaScript dari origin
kita sendiri. Bearer token tidak pernah dilampirkan otomatis, jadi tidak punya masalah ini.

Hal lain di `WebController`:
- **Session fixation** — `request.changeSessionId()` saat login.
- **Logout instan** — `session.invalidate()`. Bandingkan dengan JWT yang butuh denylist.
- **401, bukan redirect** — ini API JSON, bukan halaman HTML.

## 3. API key & HTTP Basic

```bash
curl -i localhost:8082/partner/exchange-rates -H 'X-API-Key: pk_test_mitra_ewallet_001'   # 200
curl -i localhost:8082/internal/health-detail -u ops:ops-secret                           # 200
curl -i localhost:8082/internal/health-detail -u andi:andi123                             # 403 (bukan OPS)
```

- **API key** mengidentifikasi *partner*, bukan user. Kirim lewat header (bukan query
  string, supaya tidak masuk access log). Tidak kedaluwarsa sendiri, jadi rotasi harus
  direncanakan.
- **Basic** = `base64(user:password)` di **setiap** request. Base64 bukan enkripsi, jadi
  hanya boleh di atas HTTPS.

## Decision matrix

| | Stateless? | Revoke instan | Cocok untuk | Catatan |
|---|---|---|---|---|
| Session + CSRF | Tidak | Ya | Web app satu domain | Scale-out butuh session store bersama (Redis) |
| JWT access + refresh | Access: ya | Butuh denylist | Mobile, SPA, antar-service | Access pendek (5–15 menit), refresh dirotasi |
| OAuth 2.0 Auth Code + PKCE | — | Lewat auth server | Mobile/SPA login via IdP | Password tidak pernah masuk app |
| OAuth 2.0 Client Credentials | Ya | Token pendek | Service-to-service | Pengganti API key yang lebih baik |
| OIDC | — | — | SSO / identitas user | OAuth + `id_token` (siapa user-nya) |
| API key | Ya | Hapus key | Partner sederhana | Tanpa expiry & scope bawaan |
| Basic | Ya | Ganti password | Internal, tooling | Wajib HTTPS |
| mTLS | Ya | Revoke sertifikat | Service-to-service zero-trust | Identitas di level TLS, operasional berat (CA, rotasi) |

> **mTLS** belum diimplementasikan di modul ini. Untuk mencobanya: buat CA + sertifikat
> server/client dengan `keytool`/`openssl`, set `server.ssl.client-auth=need` di Spring Boot,
> lalu `curl --cert client.crt --key client.key --cacert ca.crt https://localhost:8443/...`.

## Yang perlu diperhatikan di kode

- **`SecurityConfig`** — empat `SecurityFilterChain` dengan `@Order`, dipilih berdasarkan
  prefix path. Aturan otorisasi (scope/role) ada di sini, bukan tersebar di controller.
- **`JwtService`** — issue & verify HS256. Urutan verifikasi: format → `alg` → signature
  (constant-time) → `exp` → denylist `jti`.
- **`BearerTokenFilter` + `BearerErrorHandlers`** — 401 vs 403 dengan header
  `WWW-Authenticate` sesuai RFC 6750.
- **`RefreshTokenStore`** — refresh token opaque, rotation, reuse detection.
- **`AuthDemoTest`** — semua skenario di atas sebagai test otomatis.
