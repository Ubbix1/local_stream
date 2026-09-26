#!/usr/bin/env bash
# Generates a release keystore (JKS) for LocalStream signing.
# Usage:
#   ./scripts/generate_release_keystore.sh [-p password] [-a localstream]
# Leaves android/release-keystore.jks and android/key.properties ready to use.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
keystore="$repo_root/apps/mobile/android/release-keystore.jks"
alias_name="localstream"
password=""

while getopts "a:p:" opt; do
  case "$opt" in
    a) alias_name="$OPTARG" ;;
    p) password="$OPTARG" ;;
    *) echo "Usage: $0 [-a alias] [-p password]" >&2; exit 1 ;;
  esac
done

if [ -z "$password" ]; then
  password="$(openssl rand -hex 18)"
fi

if [ -f "$keystore" ]; then
  echo "Keystore already exists: $keystore"
else
  keytool -genkeypair -v \
    -keystore "$keystore" \
    -storetype JKS \
    -alias "$alias_name" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$password" -keypass "$password" \
    -dname "CN=LocalStream, OU=Mobile, O=LocalStream, L=Local, ST=Local, C=US"
fi

key_props="$repo_root/apps/mobile/android/key.properties"
cat > "$key_props" <<EOF
storeFile=android/release-keystore.jks
storePassword=$password
keyAlias=$alias_name
keyPassword=$password
EOF

echo "Keystore created: $keystore"
echo "key.properties written: $key_props"
echo "Store password: $password  (keep it safe; if lost the key is unusable)"
echo "Add these same values as CI secrets: LS_KEYSTORE_BASE64, LS_STORE_PASSWORD, LS_KEY_ALIAS, LS_KEY_PASSWORD"