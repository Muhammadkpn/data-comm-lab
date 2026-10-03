#!/usr/bin/env bash
# Membuat sertifikat self-signed untuk localhost, HANYA untuk belajar TLS/HTTP2 di lokal.
# Hasilnya di .certs/ (di-.gitignore) -- private key tidak pernah di-commit.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .certs
rm -f .certs/dev-keystore.p12 .certs/dev-cert.pem

keytool -genkeypair -alias data-comm-lab -keyalg RSA -keysize 2048 -validity 365 \
    -dname "CN=localhost, O=data-comm-lab" -ext "SAN=dns:localhost,ip:127.0.0.1" \
    -storetype PKCS12 -keystore .certs/dev-keystore.p12 -storepass changeit -keypass changeit >/dev/null

# Sertifikat publiknya, untuk `curl --cacert` (supaya tidak perlu -k / --insecure).
keytool -exportcert -rfc -alias data-comm-lab -keystore .certs/dev-keystore.p12 \
    -storepass changeit -file .certs/dev-cert.pem >/dev/null

echo "Dibuat: .certs/dev-keystore.p12 (server) dan .certs/dev-cert.pem (untuk curl --cacert)"
