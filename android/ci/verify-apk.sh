#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-or-later
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
source "$ROOT/android/ci/guard.sh"
APK=$(realpath "${1:?APK path required}")
KIND=${2:?debug, unsigned or release}
mkdir -p "$ROOT/android/reports/apk-$KIND"
REPORT="$ROOT/android/reports/apk-$KIND"
zipalign -c -P 16 -v 4 "$APK" > "$REPORT/alignment.txt"
apkanalyzer manifest print "$APK" > "$REPORT/manifest.xml"
python3 "$ROOT/android/ci/validate-apk.py" "$APK" "$REPORT/manifest.xml" "$KIND"
if [[ "$KIND" != unsigned ]]; then
  apksigner verify --verbose --print-certs "$APK" > "$REPORT/signature.txt"
fi
sha256sum "$APK" > "$REPORT/SHA256SUMS"
