#!/usr/bin/env bash
# Shortcut untuk menjalankan main class tiap modul tanpa mengetik
# `mvn -pl <modul> compile exec:java -Dexec.mainClass=...` yang panjang.
#
# Pemakaian:
#   scripts/run.sh                    -> tampilkan daftar target
#   scripts/run.sh tcp server         -> jalankan TransferServer di tcp-raw-demo
#   scripts/run.sh tcp client         -> (di terminal lain) jalankan client-nya
set -euo pipefail

cd "$(dirname "$0")/.."

# format: "<alias> <target>|<modul>|<main class atau 'boot'>"
TARGETS=$(cat <<'LIST'
tcp server|tcp-raw-demo|com.learn.datacomm.tcpraw.TransferServer
tcp client|tcp-raw-demo|com.learn.datacomm.tcpraw.TransferClient
tcp bad-framing|tcp-raw-demo|com.learn.datacomm.tcpraw.BadFramingDemo
udp server|udp-demo|com.learn.datacomm.udp.TransferServer
udp client|udp-demo|com.learn.datacomm.udp.TransferClient
rest server|rest-demo|boot
rest client|rest-demo|com.learn.datacomm.rest.client.TransferClient
rest drift|rest-demo|com.learn.datacomm.rest.client.SchemaDriftClient
rest idempotency|rest-demo|com.learn.datacomm.rest.client.IdempotencyClient
rest pagination|rest-demo|com.learn.datacomm.rest.client.PaginationClient
grpc server|grpc-demo|com.learn.datacomm.grpc.TransferServer
grpc client|grpc-demo|com.learn.datacomm.grpc.TransferClient
grpc drift|grpc-demo|com.learn.datacomm.grpc.SchemaDriftDemo
graphql server|graphql-demo|boot
iso8583 server|iso8583-demo|com.learn.datacomm.iso8583.TransferServer
iso8583 client|iso8583-demo|com.learn.datacomm.iso8583.TransferClient
ofs server|ofs-demo|com.learn.datacomm.ofs.TransferServer
ofs client|ofs-demo|com.learn.datacomm.ofs.TransferClient
auth server|auth-demo|boot
realtime server|realtime-demo|boot
realtime client|realtime-demo|com.learn.datacomm.realtime.client.RealtimeComparisonClient
gateway server|gateway-demo|boot
gateway burst|gateway-demo|com.learn.datacomm.gateway.client.BurstClient
LIST
)

usage() {
    echo "Pemakaian: scripts/run.sh <demo> <target>"
    echo
    echo "Target yang tersedia:"
    echo "$TARGETS" | awk -F'|' '{printf "  %-22s (%s)\n", $1, $2}'
    exit 1
}

[ $# -eq 2 ] || usage

line=$(echo "$TARGETS" | grep "^$1 $2|" || true)
[ -n "$line" ] || usage

module=$(echo "$line" | cut -d'|' -f2)
main=$(echo "$line" | cut -d'|' -f3)

if [ ! -d "$module" ]; then
    echo "Modul $module belum ada di repo ini." >&2
    exit 1
fi

if [ "$main" = "boot" ]; then
    exec mvn -q -pl "$module" spring-boot:run
else
    exec mvn -q -pl "$module" compile exec:java -Dexec.mainClass="$main"
fi
