# realtime-demo

Belajar **real-time communication** (bab 14 materi): empat cara client mengikuti status
transfer yang berubah di server. Server memproses transfer melewati 5 stage
(`RECEIVED → VALIDATING → DEBIT_SOURCE → CREDIT_DESTINATION → COMPLETED`), berganti setiap
1 detik. Pertanyaannya: bagaimana client tahu secepat mungkin, dengan biaya serendah mungkin?

Konteks Aira: notifikasi saat ini masih **polling-based**. Modul ini memperlihatkan apa yang
didapat (dan dibayar) kalau pindah ke pola lain.

## Cara run

**Terminal 1 — server** (port 8083):
```bash
scripts/run.sh realtime server
```

**Terminal 2 — client pembanding:**
```bash
scripts/run.sh realtime client
```

Atau buka **http://localhost:8083** di browser, klik tiap tombol sambil melihat
DevTools → Network.

## Hasil yang sudah diverifikasi

```
=== SHORT POLLING (tiap 1500 ms) ===
[CLIENT] poll #1        stage=RECEIVED            terlambat   21 ms
[CLIENT] poll #2        stage=VALIDATING          terlambat  525 ms
[CLIENT]   ... 1 stage TERLEWAT
[CLIENT] poll #3        stage=CREDIT_DESTINATION  terlambat   29 ms

=== SHORT POLLING (tiap 400 ms) ===
[CLIENT] poll #2        (belum berubah -- request sia-sia)
[CLIENT] poll #3        (belum berubah -- request sia-sia)
[CLIENT] poll #4        stage=VALIDATING          terlambat  216 ms
...

=== Ringkasan (5 stage di server) ===
Pola                Request Stage terlihat  Rata-rata delay
Polling 1500ms            4             4           265 ms
Polling 400ms            11             5           105 ms
Long polling              5             5             3 ms
SSE                       1             5             1 ms
WebSocket                 1             5            35 ms
```

Dilema short polling terlihat jelas: interval panjang membuat stage **terlewat** dan update
**terlambat**, sedangkan interval pendek menghasilkan banyak **request sia-sia** (bayangkan
dikalikan jumlah user aktif). Delay WebSocket di stage pertama lebih besar karena mencakup
handshake. Setelah koneksi terbuka, delay-nya setara SSE.

## Keempat pola

### Short polling — `GET /transfers/{id}/status`
Client bertanya setiap N detik. Paling sederhana dan tanpa state di server, tapi selalu
memilih antara boros dan lambat.

### Long polling — `GET /transfers/{id}/status/long-poll?since=<version>`
Server **menahan** request sampai ada versi lebih baru dari `since`, atau menjawab `204`
setelah timeout lalu client langsung bertanya lagi. Update hampir instan, tetap HTTP biasa
(lolos semua proxy/firewall). Di server dipakai `DeferredResult`: thread servlet dilepas
selama menunggu, jadi ribuan long-poll tidak memakan ribuan thread.

### Server-Sent Events — `GET /transfers/{id}/events`
Satu response `text/event-stream` yang terus terbuka:

```bash
curl -N localhost:8083/transfers/TRF-0001/events
# id:1
# event:status
# data:{"transferId":"TRF-0001","stage":"RECEIVED","version":1,...}
#
# id:2
# ...
```

Searah (server → client), teks, dan auto-reconnect bawaan browser (`EventSource`). Saat
reconnect, browser mengirim `Last-Event-ID` dan server hanya mengirim event yang terlewat:

```bash
curl -N localhost:8083/transfers/TRF-0001/events -H 'Last-Event-ID: 3'   # hanya id 4 dan 5
```

### WebSocket — `ws://localhost:8083/ws/transfers`
Diawali HTTP `Upgrade: websocket` → `101 Switching Protocols`, lalu koneksi TCP yang sama
dipakai untuk frame **dua arah**. Protokol aplikasinya kita definisikan sendiri:
`SUBSCRIBE <id>`, `PING` → `PONG`. Routing, status code, dan format error HTTP tidak berlaku
lagi, jadi semua harus didesain ulang (mirip kembali ke `tcp-raw-demo`).

## Decision

| | Short polling | Long polling | SSE | WebSocket |
|---|---|---|---|---|
| Arah | client → server | client → server | server → client | dua arah |
| Latensi update | ≤ interval | ~instan | ~instan | ~instan |
| Request per update | banyak (termasuk sia-sia) | 1 | 0 (stream) | 0 (frame) |
| Koneksi terbuka di server | tidak | selama menunggu | terus | terus |
| Lewat proxy/LB biasa | ya | ya (perhatikan timeout) | ya (matikan buffering) | butuh dukungan Upgrade |
| Reconnect / resume | n/a | manual (`since`) | bawaan (`Last-Event-ID`) | manual |
| Cocok untuk | data jarang berubah, sederhana | fallback universal | notifikasi, progress, feed | chat, kolaborasi, trading |

Untuk **status transfer / notifikasi** (server → client saja), SSE biasanya pilihan
termurah. WebSocket baru sepadan jika client juga sering mengirim data. Untuk mobile app
yang tidak sedang dibuka, ketiganya kalah dari **push notification** (FCM/APNs).

Catatan operasional yang sering terlupa:
- Koneksi panjang (long poll/SSE/WS) memakan file descriptor & memori per user, dan load
  balancer harus dikonfigurasi idle-timeout-nya.
- Jika server di-scale ke banyak instance, update harus disebar ke instance tempat client
  terhubung (Redis pub/sub, Kafka) — `TransferTracker` di demo ini hanya in-memory.
- WebSocket wajib membatasi **Origin** (`setAllowedOrigins`) untuk mencegah Cross-Site
  WebSocket Hijacking.

## Yang perlu diperhatikan di kode

- **`TransferTracker`** — `current()` (dibaca polling) vs `subscribe()` (push). Itu
  perbedaan mendasar antar pola.
- **`TransferStatusController.longPoll`** — subscribe dulu, *lalu* cek state, supaya update
  yang terjadi di antara keduanya tidak hilang.
- **`TransferStatusController.events`** — `SseEmitter`, id event = versi, dukungan `Last-Event-ID`.
- **`TransferWebSocketHandler`** — `TextWebSocketHandler` mentah (tanpa STOMP) supaya frame-nya terlihat.
- **`RealtimeDemoTest`** — long-poll menahan & 204, SSE + resume, WebSocket dua arah.
