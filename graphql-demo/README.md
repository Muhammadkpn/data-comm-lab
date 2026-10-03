# graphql-demo

Belajar **GraphQL** (Spring for GraphQL) dengan skenario yang sama seperti modul lain:
transfer dana antar rekening — plus cek saldo & riwayat transaksi. Fokus: satu endpoint
yang bisa menjawab kebutuhan data berbeda-beda tergantung field yang diminta client, dan
N+1 query problem yang khas di GraphQL saat resolver nested tidak di-batch.

## Cara run

**Jalankan server:**
```bash
mvn -pl graphql-demo spring-boot:run
```
Server jalan di `http://localhost:8081`, endpoint GraphQL di `POST /graphql`.

## Cara akses GraphiQL

Buka `http://localhost:8081/graphiql` di browser. Ini UI in-browser bawaan Spring for
GraphQL untuk menulis & menjalankan query/mutation secara manual, lengkap dengan
autocomplete berdasarkan schema — tidak perlu Postman/Insomnia terpisah (kalau lebih
familiar dengan Apollo Studio / GraphQL Playground versi lain, endpoint `/graphql` yang
sama juga bisa dipakai di sana).

> **Catatan:** halaman GraphiQL bawaan `spring-boot-starter-graphql` aslinya memuat
> React & GraphiQL langsung dari `unpkg.com` saat runtime. Kalau CDN itu diblokir di
> browser kamu (ad-blocker/privacy extension/firewall korporat), halaman akan macet di
> tulisan "Loading...". Supaya demo ini tetap jalan tanpa tergantung koneksi internet,
> `src/main/resources/graphiql/index.html` di-override dengan versi yang memuat semua
> asset (React, ReactDOM, GraphiQL JS+CSS) dari `src/main/resources/static/graphiql/vendor/`
> secara lokal.

**Cara pakai GraphiQL**: editor utama ada di panel kiri (yang paling besar) — di situlah
query/mutation ditulis. Panel kecil di bawahnya ("Variables", "Headers") untuk kebutuhan
lain, BUKAN tempat menulis query — kalau query ditulis di situ akan muncul error
`Variables are invalid JSON` karena panel itu memang mengharapkan JSON, bukan GraphQL.
Setelah query ditulis di panel utama, jalankan dengan tombol ▶ atau `Ctrl+Enter`.

## Skenario query yang disarankan dicoba

### 1. Basic query
```graphql
query {
  accounts {
    id
    ownerName
    balance
  }
}
```

### 2. Field terbatas — keunggulan GraphQL dibanding REST

Client cukup minta nama & saldo, skip histori transaksi sama sekali:
```graphql
query {
  account(id: "ACC-001") {
    ownerName
    balance
  }
}
```
Bandingkan dengan skenario #3 di bawah — **keduanya lewat endpoint `/graphql` yang
sama**, tidak ada endpoint REST terpisah semacam `/accounts/{id}/summary` vs
`/accounts/{id}/detail`. Bentuk response ditentukan client lewat field yang diminta,
bukan oleh server lewat URL yang berbeda.

### 3. Field lengkap — saldo + 5 transaksi terakhir
```graphql
query {
  account(id: "ACC-001") {
    ownerName
    balance
    transactions(limit: 5) {
      type
      amount
      timestamp
    }
  }
}
```

### 4. Trigger N+1 problem

Query field `transactionsNaive` (resolver naif, lihat `NaiveTransactionResolver`) untuk
semua akun sekaligus:
```graphql
query {
  accounts {
    id
    transactionsNaive {
      amount
    }
  }
}
```
Lihat log server: baris `[N+1-NAIVE] Pemanggilan #1`, `#2`, `#3`, `#4` — satu pemanggilan
resolver TERPISAH per akun (4 akun = 4 "query" ke data source, padahal client cuma
mengirim satu request GraphQL).

Sekarang jalankan query yang sama tapi pakai field `transactions` (resolver dengan
DataLoader, lihat `TransactionDataLoader` + `TransactionResolver`):
```graphql
query {
  accounts {
    id
    transactions {
      amount
    }
  }
}
```
Bandingkan log-nya: hanya **satu** baris `[DATALOADER-BATCH] Pemanggilan #1 -- 1 query
batch untuk 4 akun sekaligus`. Response GraphQL-nya identik strukturnya — bedanya murni
di efisiensi resolver, tidak terlihat dari sisi client sama sekali.

### 5. Mutation — proses transfer
```graphql
mutation {
  transfer(
    sourceAccountId: "ACC-001"
    destinationAccountId: "ACC-002"
    amount: 50000
    referenceId: "REF-GQL-01"
  ) {
    status
    message
    sourceAccount {
      balance
    }
    destinationAccount {
      balance
    }
  }
}
```
Satu mutation langsung mengembalikan saldo terbaru KEDUA akun — tidak perlu GET/query
terpisah setelahnya seperti pola REST "POST lalu GET ulang untuk refresh data".

### Coba manual pakai curl

```bash
curl -s -X POST http://localhost:8081/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "query { account(id: \"ACC-001\") { ownerName balance } }"}'
```

## Alur komunikasi

```
CLIENT                                          SERVER
  |                                                |
  |--- POST /graphql                               |
  |    Content-Type: application/json ------------>|
  |    { "query": "{ account(id: \"ACC-001\")       |
  |        { ownerName balance } }" }               |
  |                                                 |  Spring for GraphQL:
  |                                                 |  - parse & validasi query vs schema.graphqls
  |                                                 |  - panggil resolver HANYA untuk field yang diminta
  |                                                 |  - susun response sesuai bentuk query, bukan bentuk tetap
  |                                                 |
  |<--- 200 OK                                      |
  |     { "data": { "account": { ... } } } ---------|
```

Beda dengan REST (`rest-demo`): status code HTTP nyaris selalu `200 OK`, sukses/gagal
ditentukan lewat isi field `data` vs `errors` di body JSON — bukan lewat status code
seperti 201/422/404. GraphQL punya "kontrak status" sendiri yang terpisah dari lapisan
transport HTTP.

## Teori & cara kerja end-to-end

### Apa itu GraphQL, secara konsep

GraphQL adalah **query language untuk API** plus runtime di server untuk mengeksekusinya.
Bedanya paling mendasar dengan REST:

- **REST**: satu resource = satu URL. Butuh data ringkas dan data lengkap untuk resource
  yang sama? Biasanya butuh dua endpoint (`/accounts/{id}/summary` vs
  `/accounts/{id}/detail`), atau satu endpoint yang selalu mengembalikan semua field
  (boros) atau dikontrol lewat query param ad-hoc yang tidak standar.
- **GraphQL**: satu URL (`/graphql`) untuk semua operasi. Client mengirim sebuah
  **selection set** — daftar field yang benar-benar dia butuhkan, termasuk field
  bersarang (nested) — dan server hanya mengeksekusi & mengembalikan persis field itu.
  Bentuk response ditentukan oleh **query dari client**, bukan oleh **endpoint dari
  server**. Ini yang dipraktikkan langsung di skenario #2 vs #3 di atas.

### Tiga pilar yang dipakai di modul ini

1. **Schema (SDL)** — `schema.graphqls` adalah kontrak: semua type, field, argumen, Query,
   dan Mutation yang server sediakan didefinisikan di sini lebih dulu ("schema-first").
   Perannya mirip `transfer.proto` di `grpc-demo`, tapi SDL ini **dibaca & dieksekusi
   langsung saat runtime** oleh graphql-java — tidak ada tahap code generation/compile
   seperti `protoc` yang menghasilkan class Java dari `.proto`.
2. **Query vs Mutation** — `type Query` dan `type Mutation` di schema adalah dua
   "pintu masuk" (root type) yang berbeda secara konvensi: field di bawah `query { }`
   dieksekusi **paralel** oleh executor (aman, karena cuma baca), field di bawah
   `mutation { }` dieksekusi **berurutan** (supaya efek samping predictable kalau
   client mengirim lebih dari satu mutation dalam satu request). Ini konvensi GraphQL,
   bukan aturan HTTP method seperti GET/POST di REST.
3. **Resolver** — fungsi Java (method yang dianotasi `@QueryMapping`, `@MutationMapping`,
   atau `@SchemaMapping`) yang "mengisi" satu field tertentu di schema. GraphQL TIDAK
   membaca field langsung dari objek Java hasil query database seperti serializer JSON
   biasa — setiap field di response punya resolver-nya sendiri (kadang implisit lewat
   getter, kadang eksplisit lewat method beranotasi), dan executor hanya memanggil
   resolver untuk field yang benar-benar diminta client.

### Workflow end-to-end, langkah demi langkah

Ambil contoh skenario #3 di atas (`account(id: "ACC-001") { ownerName balance
transactions(limit: 5) { type amount timestamp } }`) sebagai contoh konkret:

```
1. CLIENT --- POST /graphql, body { "query": "{ account(id: \"ACC-001\") { ... } }" }
                    |
                    v
2. GraphQlWebMvcAutoConfiguration menerima request di controller HTTP bawaan
   Spring for GraphQL, ambil string query dari body JSON.
                    |
                    v
3. graphql-java PARSE string query jadi AST (Abstract Syntax Tree) -- struktur
   pohon yang merepresentasikan field apa saja yang diminta, termasuk nesting-nya.
                    |
                    v
4. Executor VALIDASI AST terhadap schema.graphqls: apakah field "account",
   "ownerName", "transactions", dst benar-benar ada di schema, apakah argumen
   "id" dan "limit" tipenya cocok. Kalau tidak cocok, request ditolak di sini
   -- response langsung berisi "errors", resolver TIDAK PERNAH dipanggil.
                    |
                    v
5. Executor MULAI EKSEKUSI, field demi field, dari root:
   a. Field "account" di root Query -> panggil AccountQueryResolver.account("ACC-001")
      (method beranotasi @QueryMapping). Method ini mengembalikan objek Account utuh
      dari AccountStore.
   b. Untuk tiap field di dalam selection "account { ... }": "ownerName" dan
      "balance" diambil lewat getter biasa (Account.getOwnerName(),
      Account.getBalance()) -- GraphQL Java otomatis memetakan nama field schema
      ke getter Java dengan nama sama.
   c. Field "transactions" TIDAK diambil lewat getter -- ia dipetakan ke resolver
      terpisah (TransactionResolver, @SchemaMapping typeName="Account"
      field="transactions") karena field ini butuh logika lain (ambil dari
      AccountStore.findTransactions, terapkan argumen "limit"). Resolver inilah
      yang benar-benar mengeksekusi lookup transaksi.
                    |
                    v
6. Executor MERAKIT hasil semua resolver itu jadi satu struktur JSON yang bentuknya
   PERSIS mengikuti selection set yang diminta client -- field yang tidak diminta
   (mis. "id" akun kalau tidak ditulis di query) TIDAK PERNAH dieksekusi resolvernya
   dan TIDAK muncul di response sama sekali. Ini akar dari "field selection" yang
   didemokan di skenario #2 vs #3.
                    |
                    v
7. CLIENT <--- 200 OK, body { "data": { "account": { "ownerName": ..., "balance": ...,
   "transactions": [ ... ] } } }
```

Diagram "Alur komunikasi" di atas meringkas langkah 2-6 jadi satu kotak "Spring for
GraphQL" — bagian ini memperjelas apa saja yang terjadi di dalam kotak itu.

### Bagaimana N+1 & DataLoader cocok ke alur ini

Langkah 5c di atas ("field yang butuh resolver terpisah") adalah PERSIS titik di mana
N+1 problem muncul, begitu field itu ada di dalam sebuah **list**:

- Query `accounts { id transactionsNaive { amount } }` membuat executor pertama-tama
  menjalankan `AccountQueryResolver.accounts()` (langkah 5a, root) yang mengembalikan
  4 objek `Account`.
- Untuk TIAP `Account` di list itu, executor mengeksekusi field `transactionsNaive`
  secara independen -- ia tidak tahu (dan tidak peduli) bahwa 4 pemanggilan resolver
  yang sama bisa digabung. Karena itu `NaiveTransactionResolver.transactionsNaive(...)`
  terpanggil 4 kali terpisah, masing-masing "mengejar" data untuk satu `accountId`.
  Inilah **1 query untuk `accounts` + N query untuk `transactions`-nya = N+1 total**.
- `TransactionDataLoader` mengubah urutan kejadian itu: saat executor mengeksekusi
  field `transactions` untuk tiap akun, `TransactionResolver` (lihat
  `src/main/java/com/learn/datacomm/graphql/resolver/TransactionResolver.java`) tidak
  langsung mengambil data -- ia hanya memanggil `dataLoader.load(accountId)`, yang
  HANYA mendaftarkan id itu ke sebuah antrean. Spring for GraphQL menahan eksekusi
  batch-nya sampai semua field di level itu selesai "mendaftar" (dalam satu tick event
  loop), baru men-dispatch **satu** pemanggilan berisi seluruh `accountId` yang
  terkumpul ke fungsi yang didaftarkan lewat `BatchLoaderRegistry` di
  `TransactionDataLoader.java`. Hasilnya: 4 akun tetap 1 pemanggilan ke data source,
  bukan 4.

Perbandingan ini bisa dilihat langsung di log server lewat skenario #4 di atas —
`[N+1-NAIVE] Pemanggilan #1..#4` vs `[DATALOADER-BATCH] Pemanggilan #1` untuk jumlah
akun yang sama.

## Kalau data diambil dari tabel database sungguhan

Modul ini sengaja pakai `AccountStore` in-memory (`Map` biasa) supaya fokusnya ke
mekanisme GraphQL, bukan setup database. Tapi penting dipahami: **mengganti sumber data
ke database sungguhan TIDAK mengubah schema, TIDAK mengubah signature resolver, dan
TIDAK mengubah query dari sisi client sama sekali** — yang berubah murni ISI method di
dalam resolver/data-access layer.

### Peta dari kode sekarang ke versi database (mis. Spring Data JPA)

| Yang ada sekarang (in-memory) | Versi dengan database |
|---|---|
| `AccountStore.findById(id)` — lookup `Map` | `AccountRepository.findById(id)` — `SELECT * FROM accounts WHERE id = ?` |
| `AccountStore.findAll()` — `Map.values()` | `AccountRepository.findAll()` — `SELECT * FROM accounts` |
| `AccountStore.findTransactions(accountId, limit)` | `SELECT * FROM transactions WHERE account_id = ? ORDER BY timestamp DESC LIMIT ?` |
| `AccountStore.applyTransfer(...)` — update 2 objek Java di memory | method `@Transactional`: `UPDATE accounts SET balance = ...` (2 baris) + `INSERT INTO transactions` (2 baris), dibungkus satu transaksi database supaya debit & kredit atomik |

Resolver seperti `AccountQueryResolver.account(String id)` di
`src/main/java/com/learn/datacomm/graphql/resolver/AccountQueryResolver.java` isinya
tetap `return accountStore.findById(id);` — hanya saja `AccountStore` di-refactor jadi
memanggil repository JPA alih-alih `Map`. Dari sudut pandang schema dan client, tidak
ada bedanya sama sekali.

### Kenapa N+1 JUSTRU jauh lebih nyata dampaknya dengan database sungguhan

Di modul ini, "N+1" cuma dibuktikan lewat counter di `System.out.println` — biayanya
kecil karena `AccountStore.findTransactions` cuma lookup `Map` di memory yang sama.
Begitu `AccountStore` diganti `AccountRepository` (JPA) yang benar-benar bicara ke
database:

- `NaiveTransactionResolver` yang tadinya memanggil `findTransactions` 4 kali (satu per
  akun) akan menjadi **4 query SQL terpisah yang sungguhan** — 4 round-trip jaringan ke
  database, 4 kali query planning, 4 kali I/O disk. Untuk halaman dengan 50 akun,
  ini jadi 50 query SQL cuma untuk menampilkan riwayat transaksinya — walaupun query
  GraphQL yang dikirim client tetap cuma SATU request HTTP.
- Versi `TransactionDataLoader` bisa diubah supaya fungsi batch-nya memanggil **satu**
  query SQL `WHERE account_id IN (...)` untuk semua id yang terkumpul, lalu hasilnya
  dikelompokkan per `accountId` di sisi Java. Ini mengubah 50 query jadi 1 query,
  penghematan yang jauh lebih signifikan dan nyata (bisa diukur di query log database)
  dibanding versi in-memory yang cuma beda jumlah baris log.

Ilustrasi perubahan di `TransactionDataLoader.registerMappedBatchLoader` (bukan kode
yang sungguhan ditambahkan ke project — cukup gambaran arahnya):

```java
// Sekarang (in-memory): loop per accountId, tiap iterasi lookup Map terpisah
Map<String, List<Transaction>> result = accountIds.stream()
        .collect(Collectors.toMap(
                accountId -> accountId,
                accountId -> accountStore.findTransactions(accountId, null)));

// Kalau pakai JPA: SATU query untuk semua accountId, baru dikelompokkan di Java
List<Transaction> semuaTransaksi =
        transactionRepository.findByAccountIdIn(accountIds);
Map<String, List<Transaction>> result = semuaTransaksi.stream()
        .collect(Collectors.groupingBy(Transaction::getAccountId));
```

Signature fungsi batch loader (`(Set<String> accountIds, ...) -> Mono<Map<String,
List<Transaction>>>`) TIDAK berubah bentuk sama sekali — yang berubah cuma isi
implementasinya. Ini poin pentingnya: pola resolver + DataLoader di GraphQL dirancang
supaya penggantian sumber data (in-memory → database → bahkan microservice lain lewat
HTTP) tidak memaksa perubahan di schema atau di sisi client.

Catatan: modul ini TETAP in-memory sesuai scope BASIC-nya — bagian ini murni penjelasan
konseptual untuk menjembatani ke kasus dunia nyata, bukan instruksi untuk mengubah kode
di modul ini.

## Bagaimana schema tahu resolver mana yang dipakai

Pertanyaan yang sering muncul: dari `schema.graphqls`, bagaimana Spring for GraphQL tahu
harus memanggil method Java yang mana untuk tiap field? Ini bukan config terpusat di satu
file — pencocokannya (di GraphQL disebut *binding* field ke *resolver*, atau secara teknis
ke `DataFetcher`) terjadi lewat **konvensi + anotasi**, dicek SEKALI saat aplikasi start.

### Tiga jalur binding, dari root sampai field bersarang

1. **Root field (`Query`/`Mutation`) → dicocokkan lewat nama method.** Method yang
   dianotasi `@QueryMapping` atau `@MutationMapping` dipetakan ke field di schema yang
   NAMANYA SAMA PERSIS dengan nama method Java-nya:
   ```java
   @QueryMapping
   public Account account(@Argument String id) { ... }   // -> field "account" di type Query

   @QueryMapping
   public List<Account> accounts() { ... }                 // -> field "accounts" di type Query
   ```
   Lihat `AccountQueryResolver.java` dan `TransferMutationResolver.java` untuk contoh
   nyatanya. Kalau nama method mau beda dari nama field, bisa dipaksa eksplisit lewat
   `@QueryMapping("namaField")`.

2. **Field bersarang yang datanya sudah tersedia di objek induk → getter otomatis.**
   Field seperti `ownerName` dan `balance` di type `Account` TIDAK punya resolver
   tertulis sama sekali di project ini — begitu `AccountQueryResolver.account(...)`
   mengembalikan objek `Account`, graphql-java otomatis memanggil `getOwnerName()` dan
   `getBalance()` untuk mengisi field dengan nama yang cocok (lewat mekanisme
   `PropertyDataFetcher` bawaan graphql-java, dipakai sebagai fallback kalau tidak ada
   resolver eksplisit terdaftar untuk field itu).

3. **Field bersarang yang butuh logika/data tambahan → `@SchemaMapping` eksplisit.**
   Field `transactions` tidak bisa dijawab getter biasa (butuh lookup terpisah +
   argumen `limit`), jadi acuannya bergeser dari nama method ke **atribut** anotasi:
   ```java
   @SchemaMapping(typeName = "Account", field = "transactions")
   public CompletableFuture<List<Transaction>> transactions(Account account, @Argument Integer limit, ...) { ... }
   ```
   `typeName` = nama type di schema (`Account`), `field` = nama field di type itu
   (`transactions`). Nama method Java (`transactions`) di sini kebetulan sama, tapi
   TIDAK relevan untuk pencocokan — yang dipakai murni dua atribut itu. Lihat
   `TransactionResolver.java` dan `NaiveTransactionResolver.java` (field
   `transactionsNaive`) untuk contoh nyatanya.

### Aturan cakupan: satu field, tepat satu resolver

Tidak ada mekanisme "beberapa resolver untuk field yang sama, lalu salah satu dipilih"
di GraphQL/Spring for GraphQL — tidak ada priority, override, atau chain seperti
middleware. Secara internal, Spring for GraphQL membangun sebuah `Map` yang key-nya adalah
pasangan `(typeName, field)` — kalau dua method berbeda mencoba mendaftar untuk key yang
sama, Spring **melempar `IllegalStateException` ("Ambiguous mapping...") saat aplikasi
start**, BUKAN memilih salah satu secara diam-diam saat runtime. Kalau kombinasi field
itu tidak pernah dites lewat query, error ini baru muncul begitu aplikasi dijalankan
(karena pengecekan terjadi sekali di awal, saat semua `@Controller` di-scan) — jadi
kesalahannya ketahuan lebih awal, bukan pas ada request yang kena field itu.

Ini alasan konkret kenapa `NaiveTransactionResolver` dan `TransactionResolver` di project
ini dipetakan ke **nama field yang beda** (`transactionsNaive` vs `transactions`) walau
keduanya secara konsep sama-sama "menjawab riwayat transaksi". Kalau dipetakan ke field
`transactions` yang sama, `graphql-demo` tidak akan bisa start sama sekali.

Untuk kombinasi getter otomatis (jalur #2) dan `@SchemaMapping` eksplisit (jalur #3)
untuk field yang sama: keduanya tidak benar-benar "bentrok" seperti dua `@SchemaMapping`
di atas, karena getter bukan resolver terdaftar — ia cuma fallback yang dipakai graphql-
java KALAU tidak ada resolver eksplisit untuk field itu. Kalau ada `@SchemaMapping` yang
sudah mendaftar untuk field itu, itu yang dipakai dan getter tidak pernah disentuh sama
sekali. Meski begitu, praktik yang baik tetap satu sumber kebenaran per field — hindari
menaruh logika penting di getter kalau field yang sama juga punya `@SchemaMapping`,
supaya tidak membingungkan pembaca kode soal mana yang sebenarnya jalan.

### Paralel ke REST (Spring MVC)

Ini sebenarnya prinsip yang sama dengan `@GetMapping("/path")` di REST: kalau dua
`@RestController` berbeda sama-sama mendaftarkan `@GetMapping("/transfers")`, Spring MVC
juga melempar error ambiguous mapping saat startup, bukan menjalankan salah satunya
secara acak. Bedanya, di REST acuan pencocokannya adalah `path` + HTTP method; di
GraphQL acuannya adalah `typeName` + `field` (untuk field mapping) atau nama field
langsung (untuk root Query/Mutation). Prinsip umumnya sama: Spring selalu memvalidasi
seluruh pemetaan sekali di awal supaya tidak ada ambiguitas yang baru ketahuan saat
request sungguhan datang.

### Ringkasan cepat

| Field di schema | Jalur binding | Resolver/getter |
|---|---|---|
| `Query.account` | Nama method + `@QueryMapping` | `AccountQueryResolver.account(...)` |
| `Account.ownerName` | Getter otomatis (tanpa resolver tertulis) | `Account.getOwnerName()` |
| `Account.transactionsNaive` | `@SchemaMapping(typeName="Account", field="transactionsNaive")` | `NaiveTransactionResolver.transactionsNaive(...)` |
| `Account.transactions` | `@SchemaMapping(typeName="Account", field="transactions")` | `TransactionResolver.transactions(...)` |

## Resolver chain untuk data nested/kompleks

### Koreksi penting: satu field = satu resolver METHOD, bukan satu class

Section sebelumnya menunjukkan pemetaan field ke resolver lewat `typeName`+`field` atau
nama method — tapi itu SELALU mengacu ke satu **method**, bukan ke satu **class**. Satu
class `@Controller` boleh berisi banyak method resolver sekaligus. Buktinya sudah ada di
project ini: `AccountQueryResolver.java` sendirian berisi DUA resolver root
(`account(...)` dan `accounts()`), sementara `transfer(...)` sengaja ditaruh di class
terpisah (`TransferMutationResolver.java`). Keduanya sah — pemisahan per class di project
ini murni supaya kode rapi per tanggung jawab (query akun vs mutation transfer vs demo
N+1), **bukan** karena GraphQL/Spring mewajibkan satu class = satu field atau satu class =
satu resolver.

### Kenapa data nested bisa terbentuk otomatis: resolver chain

Ambil lagi skenario #3 di atas sebagai contoh (query 2 tingkat: `Account` →
`Transaction`):

```graphql
query {
  account(id: "ACC-001") {
    ownerName
    balance
    transactions(limit: 5) {
      type
      amount
      timestamp
    }
  }
}
```

Yang membuat hasil nested ini terbentuk BUKAN satu resolver besar yang menyusun semuanya
sekaligus, melainkan **rantai (chain) resolver kecil-kecil** yang saling menyambung lewat
parameter:

```java
// AccountQueryResolver.java
@QueryMapping
public Account account(@Argument String id) {
    // DIPANGGIL PERTAMA -- ambil data dasar akun dari AccountStore.
    // Hasilnya BELUM berisi daftar transaksi apapun.
    return accountStore.findById(id);
}
```

```java
// TransactionResolver.java
@SchemaMapping(typeName = "Account", field = "transactions")
public CompletableFuture<List<Transaction>> transactions(
        Account account, @Argument Integer limit, DataLoader<String, List<Transaction>> transactionLoader) {
    // DIPANGGIL KEDUA -- parameter "account" di sini BUKAN input dari client,
    // tapi PERSIS objek yang dikembalikan resolver "account(...)" di atas.
    return transactionLoader.load(account.getId())...
}
```

Poin kuncinya ada di parameter pertama `transactions(Account account, ...)`: nilainya
adalah **hasil resolver induknya**, bukan sesuatu yang diketik client. Resolver
`transactions` tidak perlu tahu bagaimana `Account` itu didapat (dari `Map` in-memory,
nanti bisa dari database, dsb) — ia cuma perlu tahu cara mengambil DATA DIRINYA SENDIRI
(riwayat transaksi) dari objek induk yang sudah disediakan. Pola inilah yang disebut
**resolver chain**: setiap resolver mengurus satu potongan kecil graph, dan executor yang
merangkai potongan-potongan itu jadi satu pohon nested.

### Alur eksekusi untuk query di atas

```
1. account(id: "ACC-001")
   -> Account{ id, ownerName, balance }   (transactions BELUM diisi)

2. Executor lihat client juga minta "ownerName" dan "balance" di dalam selection
   -> diisi lewat getter otomatis: Account.getOwnerName(), Account.getBalance()
   (ini "resolver" implisit yang sudah dibahas di section sebelumnya)

3. Executor lihat client juga minta "transactions(limit: 5) { ... }"
   -> panggil transactions(account = hasil langkah 1, limit = 5)
   -> resolver ini TIDAK query langsung, hanya transactionLoader.load(accountId)

4. Executor GABUNGKAN ketiga hasil itu (langkah 1+2+3) jadi satu JSON nested:
   { "account": { "ownerName": ..., "balance": ..., "transactions": [ ...5 item... ] } }
```

Total ada **tiga** resolver berbeda yang berkontribusi ke satu response ini —
`account(...)`, getter `ownerName`/`balance`, dan `transactions(...)` — dan pengembang
tidak pernah menulis kode yang secara eksplisit "menyusun" ketiganya jadi satu objek
nested. Itu kerja executor, bukan kerja resolver.

### Di titik mana N+1 muncul kalau nesting-nya lebih ramai

Alur di atas jalan mulus untuk SATU akun. Begitu query-nya diganti jadi banyak akun
sekaligus (`accounts { transactions { ... } }`), langkah 3 di atas terjadi **per akun**
— ini persis kasus yang sudah didemokan di section "Bagaimana schema tahu resolver mana
yang dipakai" lewat `transactionsNaive` (N pemanggilan terpisah) vs `transactions` yang
sudah pakai `DataLoader` (1 pemanggilan batch), dan bisa dicoba langsung lewat skenario
#4 di atas. Section ini tidak mengulang penjelasan N+1 dari awal — intinya: makin dalam
nesting-nya dan makin banyak item di tiap level, makin besar potensi N+1-nya muncul,
karena setiap level nesting adalah titik di mana resolver bisa terpanggil berkali-kali.

### Perbandingan ke REST

Kalau skenario "saldo + riwayat transaksi" ini dibangun di REST, biasanya perlu salah
satu dari dua pendekatan: (a) client mengirim **dua request terpisah**
(`GET /accounts/ACC-001` lalu `GET /accounts/ACC-001/transactions`) dan menggabungkannya
sendiri di sisi client, atau (b) server menyediakan endpoint gabungan khusus (mis.
`GET /accounts/ACC-001?include=transactions`) yang harus didesain dan dipelihara manual
untuk tiap kombinasi kebutuhan data yang berbeda. Di GraphQL, **satu query** dari client
sudah cukup — **server** yang mengurus pemanggilan bertingkat lewat resolver chain di
atas; client tidak perlu tahu (dan tidak perlu peduli) ada berapa lapis resolver yang
bekerja di baliknya.

### Ringkasan

Satu field di schema selalu dijawab oleh satu **method** resolver (boleh di class
manapun, itu murni soal organisasi kode). Untuk data nested, resolver level bawah
menerima **hasil resolver induknya** sebagai parameter pertama, dan executor GraphQL
yang merangkai semua hasil itu jadi satu response JSON nested — pengembang tidak pernah
menulis logic penggabungan manual. Konsekuensinya: begitu nesting-nya dalam dan datanya
banyak, N+1 problem jadi risiko nyata di SETIAP level nesting, bukan cuma di satu titik
— itulah sebabnya pola DataLoader penting dipahami sejak awal, bukan cuma sebagai
optimisasi belakangan.

## Yang perlu diperhatikan di kode

- **`schema.graphqls`** — SDL (Schema Definition Language): kontrak schema-first, mirip
  peran `.proto` di `grpc-demo` tapi dieksekusi langsung di runtime lewat resolver Java,
  tanpa tahap code-gen/compile seperti protoc.
- **`AccountQueryResolver` / `TransferMutationResolver`** — `@QueryMapping` vs
  `@MutationMapping` adalah konvensi GraphQL (baca vs tulis), bukan aturan teknis seperti
  GET/POST di REST. Query field dieksekusi paralel oleh executor, mutation field
  dieksekusi berurutan.
- **`NaiveTransactionResolver` vs `TransactionResolver` + `TransactionDataLoader`** —
  inti demo N+1. Field `transactionsNaive` dan `transactions` di schema terlihat mirip,
  tapi resolver-nya beda cara mengambil data: yang satu query per-item (N+1), yang lain
  batch lewat `DataLoader` (1 batch call). Komentar lengkap ada di kedua file itu.
- Tidak ada persistence sungguhan (`AccountStore` cuma `Map` in-memory dengan seed data)
  — fokus ke mekanisme protokolnya, bukan business logic yang realistis.
- **Catatan production (tidak diimplementasikan di sini)**: endpoint `/graphql` yang
  tunggal berarti semua proteksi yang biasanya "gratis" dari banyak endpoint REST
  terpisah (rate limiting per endpoint, auth per path) harus dipikirkan ulang di level
  field/operation — di production perlu authentication & authorization per field, rate
  limiting per client, serta query complexity/depth limiting supaya client tidak bisa
  mengirim query bersarang yang sangat dalam/lebar dan membebani server. Lihat
  `application.properties` untuk catatan yang sama.
