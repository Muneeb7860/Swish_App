#!/bin/bash
set -e

# Resolve directory of this script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SSL_DIR="$SCRIPT_DIR/../nginx/ssl"

mkdir -p "$SSL_DIR"

if [ ! -f "$SSL_DIR/swiss_app.crt" ] || [ ! -f "$SSL_DIR/swiss_app.key" ]; then
    echo "🔐 [SSL] Generating fresh self-signed TLS certificates for development..."
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
        -keyout "$SSL_DIR/swiss_app.key" \
        -out "$SSL_DIR/swiss_app.crt" \
        -subj "/C=CH/ST=Zurich/L=Zurich/O=Swiss Q-Commerce/CN=localhost"
    echo "✅ [SSL] Self-signed certificates successfully written to: $SSL_DIR"
else
    echo "🛡️ [SSL] Existing TLS certificates found in $SSL_DIR. Skipping generation."
fi
