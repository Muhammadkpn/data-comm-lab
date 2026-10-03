#!/usr/bin/env bash
#
# ==== DEMO: gRPC MEMINDAHKAN ERROR DARI RUNTIME KE COMPILE TIME ====
#
# Script ini mengubah transfer.proto secara SEMENTARA (rename field, hapus
# field, ganti tipe, tambah field) tanpa menyentuh satu baris pun kode Java,
# lalu menjalankan `mvn compile` untuk memperlihatkan apa yang terjadi.
#
# Intinya: di gRPC, ketidakselarasan antara kontrak (.proto) dan kode yang
# memakainya ketahuan saat BUILD -- bukan saat request pertama jatuh di
# production seperti di REST/JSON.
#
# AMAN dijalankan: transfer.proto selalu dikembalikan ke kondisi semula lewat
# `trap ... EXIT`, jadi tetap pulih walau script di-Ctrl+C atau error di tengah.
#
# Jalankan dari root repo maupun dari dalam grpc-demo/, dua-duanya bisa:
#   ./grpc-demo/schema-break-demo.sh

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROTO="$SCRIPT_DIR/src/main/proto/transfer.proto"
BACKUP="$(mktemp -t transfer.proto.bak.XXXXXX)"
LOG="$(mktemp -t schema-break.log.XXXXXX)"

cp "$PROTO" "$BACKUP"

# Jaring pengaman: apapun yang terjadi, proto dikembalikan seperti semula.
restore() {
  cp "$BACKUP" "$PROTO"
  rm -f "$BACKUP" "$LOG"
  echo
  echo "[RESTORE] transfer.proto sudah dikembalikan ke kondisi awal."
}
trap restore EXIT

# Ringkasan hasil tiap skenario, dicetak di akhir.
HASIL=()

# protobuf-maven-plugin 0.6.1 kadang menghapus cache-nya sendiri di
# target/protoc-dependencies lalu tidak membuatnya lagi, sehingga run
# BERIKUTNYA gagal dengan "Proto path element is not a directory".
# Itu masalah infrastruktur build, BUKAN akibat perubahan proto -- kalau tidak
# dibedakan, hasil demo jadi menyesatkan. Fungsi ini mendeteksi error tersebut
# dan mengulang compile-nya.
compile_grpc_demo() {
  # Paksa protoc men-generate ulang, SEKALI saja di awal. Tanpa ini, kode
  # generated dari skenario sebelumnya bisa tertinggal dan javac ikut
  # memakainya -- hasilnya SUCCESS palsu. (Menghapusnya di setiap percobaan
  # justru memperparah: hapus-tulis berulang berebut dengan protoc.)
  rm -rf "$SCRIPT_DIR/target/generated-sources" "$SCRIPT_DIR/target/classes" 2>/dev/null

  local percobaan=1
  while [ $percobaan -le 5 ]; do
    if mvn -pl grpc-demo compile > "$LOG" 2>&1; then
      return 0
    fi
    # Kalau javac benar-benar jalan dan menolak kode, itu hasil yang sah --
    # jangan diulang. (Dulu pola retry-nya terlalu longgar sampai ikut cocok
    # dengan pesan javac, sehingga kegagalan asli diulang dan bisa berubah
    # jadi SUCCESS palsu.)
    if grep -q 'COMPILATION ERROR' "$LOG"; then
      return 1
    fi
    # Error internal protobuf-maven-plugin (bukan akibat perubahan proto) -> ulangi.
    if grep -qE 'protobuf-maven-plugin' "$LOG"; then
      percobaan=$((percobaan + 1))
      continue
    fi
    return 1
  done
  return 1
}

# Panaskan cache dulu supaya error yang muncul nanti benar-benar error javac,
# bukan error download dependency.
echo "Menyiapkan build (compile awal)..."
if ! compile_grpc_demo; then
  echo "Compile awal GAGAL. Perbaiki dulu sebelum menjalankan demo ini:"
  tail -20 "$LOG"
  exit 1
fi
echo "Baseline OK -- kode saat ini compile bersih."
echo

# jalankan_skenario <judul> <penjelasan> <harapan: FAIL|OK> [penanda_generated]
#
# penanda_generated: potongan teks yang HARUS muncul di TransferRequest.java
# hasil generate kalau proto benar-benar ikut ter-regenerate. Dipakai untuk
# skenario yang diharapkan SUCCESS, supaya SUCCESS palsu (karena kode generated
# basi) bisa dideteksi. Boleh dikosongkan untuk skenario yang diharapkan gagal.
#
# (perubahan proto-nya sudah di-apply oleh pemanggil sebelum fungsi ini dipanggil)
jalankan_skenario() {
  local judul="$1"
  local penjelasan="$2"
  local harapan="$3"
  local penanda_generated="${4:-}"

  echo "================================================================"
  echo "  $judul"
  echo "================================================================"
  echo "$penjelasan"
  echo
  echo "--- Perubahan di transfer.proto: ---"
  if diff -q "$BACKUP" "$PROTO" > /dev/null; then
    # Pengaman: kalau sed gagal mencocokkan pola, proto tidak berubah sama
    # sekali dan hasil compile-nya tidak membuktikan apa pun.
    echo "    (tidak ada perubahan terdeteksi -- pola sed tidak cocok)"
    HASIL+=("DILEWATI (proto tak berubah) | $judul")
    echo
    return
  fi
  diff "$BACKUP" "$PROTO" | grep -E '^[<>]' | sed 's/^/    /'
  echo
  echo "--- Menjalankan: mvn -pl grpc-demo compile ---"

  local status_build=0
  compile_grpc_demo || status_build=1

  # Pengaman kedua, dijalankan SEBELUM hasil build ditafsirkan: pastikan kode
  # generated benar-benar mencerminkan proto yang barusan dipatch. Kalau protoc
  # sempat gagal dan menyisakan hasil generate lama, hasil build (SUCCESS
  # maupun FAILURE) tidak membuktikan apa-apa soal perubahan proto.
  #
  # penanda_generated diawali '!' berarti "teks ini HARUS SUDAH HILANG",
  # tanpa '!' berarti "HARUS ADA".
  local generated="$SCRIPT_DIR/target/generated-sources/protobuf/java/com/learn/datacomm/grpc/TransferRequest.java"
  if [ -n "$penanda_generated" ]; then
    local basi=0
    if [ ! -f "$generated" ]; then
      # protoc gagal total dan tidak menghasilkan apa-apa. Build memang FAILURE,
      # tapi penyebabnya bukan javac menolak kode -- jadi bukan bukti demo.
      basi=1
    else
      case "$penanda_generated" in
        '!'*)
          if grep -q "${penanda_generated#!}" "$generated"; then basi=1; fi
          ;;
        *)
          if ! grep -q "$penanda_generated" "$generated"; then basi=1; fi
          ;;
      esac
    fi
    if [ "$basi" -eq 1 ]; then
      echo "    Kode generated tidak sesuai proto yang barusan dipatch"
      echo "    (penanda '$penanda_generated') -- hasil build ini tidak dipakai."
      echo "    Ini bug cache protobuf-maven-plugin, bukan hasil demo. Ulangi script."
      HASIL+=("DILEWATI (generated basi) | $judul")
      echo
      cp "$BACKUP" "$PROTO"
      return
    fi
  fi

  if [ "$status_build" -eq 0 ]; then
    echo "    BUILD SUCCESS -- kode Java lama tetap bisa di-compile."
    if [ "$harapan" = "OK" ]; then
      HASIL+=("SUKSES (sesuai harapan) | $judul")
    else
      HASIL+=("SUKSES (TIDAK sesuai)   | $judul")
    fi
  else
    local jumlah
    jumlah=$(grep -cE '\.java:\[[0-9]+,[0-9]+\]' "$LOG" || true)

    # "COMPILATION ERROR" = javac benar-benar jalan dan menolak kode.
    # Ini penanda yang paling andal untuk membedakan bukti demo yang sah
    # dari error infrastruktur build.
    if ! grep -q 'COMPILATION ERROR' "$LOG"; then
      # Gagal tapi bukan karena javac -- jangan diklaim sebagai bukti demo.
      echo "    BUILD FAILURE, TAPI bukan karena error compiler Java:"
      grep -E '^\[ERROR\]' "$LOG" | head -3 | sed 's/^/    /'
      HASIL+=("ERROR BUILD (bukan bukti) | $judul")
    else
      echo "    BUILD FAILURE. Error dari compiler Java:"
      echo
      grep -E '\.java:\[[0-9]+,[0-9]+\]|symbol:|location:|incompatible types|required:|found:' "$LOG" \
        | sed 's|.*/grpc-demo/src/main/java/com/learn/datacomm/grpc/|    |' \
        | head -12
      echo
      echo "    Total $jumlah error, semuanya menunjuk file:baris yang tepat."
      if [ "$harapan" = "FAIL" ]; then
        HASIL+=("GAGAL COMPILE (sesuai harapan) | $judul")
      else
        HASIL+=("GAGAL COMPILE (TIDAK sesuai)   | $judul")
      fi
    fi
  fi
  echo

  # kembalikan ke proto asli sebelum skenario berikutnya
  cp "$BACKUP" "$PROTO"
}

echo "################################################################"
echo "#  Empat perubahan kontrak, KODE JAVA TIDAK DISENTUH sama sekali"
echo "################################################################"
echo

# --- Skenario 1: rename field -------------------------------------------------
sed -i.tmp 's/string reference_id = 4;/string trace_id = 4;/' "$PROTO" && rm -f "$PROTO.tmp"
jalankan_skenario \
  "1. RENAME field: reference_id -> trace_id (nomor tetap 4)" \
  "Di REST/JSON, mengganti nama field cuma bikin nilainya jadi null saat runtime.
Di gRPC, getter/setter-nya ikut berganti nama, jadi semua pemanggil langsung
ketahuan saat compile -- di sisi CLIENT maupun SERVER sekaligus." \
  "FAIL" \
  'getTraceId'

# --- Skenario 2: hapus field --------------------------------------------------
sed -i.tmp '/string destination_account = 2;/d' "$PROTO" && rm -f "$PROTO.tmp"
jalankan_skenario \
  "2. HAPUS field: destination_account" \
  "Semua kode yang masih memakai field ini gagal compile. Bandingkan REST:
field yang hilang baru terasa saat request sungguhan datang, itupun sering
hanya jadi null/0 tanpa error." \
  "FAIL" \
  '!getDestinationAccount'

# --- Skenario 3: ganti tipe data ----------------------------------------------
sed -i.tmp 's/int64 amount = 3;/string amount = 3;/' "$PROTO" && rm -f "$PROTO.tmp"
jalankan_skenario \
  "3. GANTI TIPE: int64 amount -> string amount" \
  "Compiler menolak long dipakai di setter yang sekarang minta String.
Ini kelas bug yang di REST baru meledak saat runtime (400 / parse error)." \
  "FAIL" \
  'java.lang.String getAmount'

# --- Skenario 4: tambah field (kontrol) ---------------------------------------
sed -i.tmp 's/  string reference_id = 4;/  string reference_id = 4;\n  string currency = 5;/' "$PROTO" && rm -f "$PROTO.tmp"
jalankan_skenario \
  "4. TAMBAH field baru: currency = 5  (skenario pembanding)" \
  "Penting supaya kesimpulannya tidak jadi 'semua perubahan proto pasti gagal'.
Menambah field itu backward-compatible: kode lama tidak menyentuh field baru,
jadi tetap compile. Inilah cara aman meng-evolve kontrak gRPC." \
  "OK" \
  "getCurrency"

# --- Ringkasan ----------------------------------------------------------------
echo "================================================================"
echo "  RINGKASAN"
echo "================================================================"
printf '  %s\n' "${HASIL[@]}"
cat <<'EOF'

Yang barusan terlihat:

  - Rename / hapus / ganti tipe  -> BUILD FAILURE, dengan file:baris yang persis.
    Error-nya muncul di CLIENT dan SERVER sekaligus, sebelum aplikasi jalan.
  - Tambah field                 -> BUILD SUCCESS (perubahan additive itu aman).

Inilah maksud "gRPC memindahkan risiko error dari runtime ke compile time":
kontraknya satu file (.proto) yang dipakai bersama, dan compiler yang menagih
keselarasannya. Di REST, ketiga perubahan pertama tadi tetap compile dengan
mulus dan baru gagal saat request sungguhan -- lihat rest-demo/SchemaDriftClient.

TAPI ada batasnya, dan ini yang sering disalahpahami:
compile time hanya menjamin keselarasan KODE <-> SKEMA di modul yang di-build.
Kalau client lama tidak ikut di-rebuild, compiler tidak pernah tahu, dan
kesalahannya balik lagi jadi masalah runtime yang DIAM-DIAM SALAH.
Jalankan demo berikut untuk melihatnya:

  mvn -pl grpc-demo compile exec:java \
      -Dexec.mainClass="com.learn.datacomm.grpc.SchemaDriftDemo"
EOF
