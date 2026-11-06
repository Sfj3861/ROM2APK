#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${1:-.}"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
SIGNED_PATH="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"

: "${ANDROID_HOME:?Set ANDROID_HOME to an installed Android SDK}"
: "${ROM2APK_KEYSTORE:?Set ROM2APK_KEYSTORE to your own keystore file}"
: "${ROM2APK_KEY_ALIAS:?Set ROM2APK_KEY_ALIAS to your key alias}"
: "${ROM2APK_STORE_PASSWORD:?Set ROM2APK_STORE_PASSWORD in the environment}"
: "${ROM2APK_KEY_PASSWORD:?Set ROM2APK_KEY_PASSWORD in the environment}"

command -v gradle >/dev/null || { echo "Gradle is required." >&2; exit 2; }
gradle -p "$PROJECT_DIR" --no-daemon :app:assembleRelease

test -f "$APK_PATH" || { echo "Release APK was not produced: $APK_PATH" >&2; exit 3; }

APKSIGNER="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner | sort -V | tail -n 1)"
test -n "$APKSIGNER" || { echo "Android build-tools apksigner was not found." >&2; exit 4; }

"$APKSIGNER" sign \
  --ks "$ROM2APK_KEYSTORE" \
  --ks-key-alias "$ROM2APK_KEY_ALIAS" \
  --ks-pass env:ROM2APK_STORE_PASSWORD \
  --key-pass env:ROM2APK_KEY_PASSWORD \
  --out "$SIGNED_PATH" \
  "$APK_PATH"

"$APKSIGNER" verify --verbose --print-certs "$SIGNED_PATH"
echo "Signed APK: $SIGNED_PATH"
