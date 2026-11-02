#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-or-later
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
source "$ROOT/android/ci/guard.sh"
mkdir -p "$ROOT/android/dist" "$ROOT/android/reports/release"
VERSION=0.1.0
UNSIGNED="$ROOT/android/app/build/outputs/apk/release/app-release-unsigned.apk"
OUTPUT="$ROOT/android/dist/QEMU-Android-v$VERSION-arm64.apk"
present=0
for name in ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
  if [[ -n "${!name:-}" ]]; then present=$((present + 1)); fi
done
if [[ "$present" -ne 4 ]]; then
  cp "$UNSIGNED" "$ROOT/android/dist/QEMU-Android-v$VERSION-arm64-UNSIGNED-INSPECTION.apk"
  echo 'No installable signed release was produced. Publisher secrets missing or incomplete.' | tee "$ROOT/android/dist/UNSIGNED.txt"
  (cd "$ROOT/android/dist" && sha256sum ./*.apk > SHA256SUMS)
  echo 'signed=false' >> "$GITHUB_OUTPUT"
  echo '### Unsigned inspection artifact only; no installable signed release produced.' >> "$GITHUB_STEP_SUMMARY"
  exit 0
fi
KEYSTORE=$(mktemp "$RUNNER_TEMP/qemu-keystore.XXXXXX")
trap 'rm -f "$KEYSTORE"' EXIT
chmod 600 "$KEYSTORE"
printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$KEYSTORE"
zipalign -P 16 -f 4 "$UNSIGNED" "$RUNNER_TEMP/qemu-aligned.apk"
apksigner sign --ks "$KEYSTORE" --ks-key-alias "$ANDROID_KEY_ALIAS" \
  --ks-pass env:ANDROID_KEYSTORE_PASSWORD --key-pass env:ANDROID_KEY_PASSWORD \
  --out "$OUTPUT" "$RUNNER_TEMP/qemu-aligned.apk"
"$ROOT/android/ci/verify-apk.sh" "$OUTPUT" release
(cd "$ROOT/android/dist" && sha256sum "$(basename "$OUTPUT")" > SHA256SUMS)
echo 'signed=true' >> "$GITHUB_OUTPUT"
echo '### Signed release artifact produced. Public publishing requires separate explicit opt-in.' >> "$GITHUB_STEP_SUMMARY"
