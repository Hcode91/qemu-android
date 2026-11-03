#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-or-later
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
source "$ROOT/android/ci/guard.sh"
WORK="$ROOT/android/.aosp-userspace"
REPORT="$ROOT/android/reports/aosp-userspace"
mkdir -p "$WORK/out" "$REPORT"
exec > >(tee "$REPORT/native-build.log") 2>&1
# The source subset is fetched by the preceding workflow step, before container build.
docker build --platform linux/amd64 --tag qemu-android-jb-native "$ROOT/android/jellybean"
docker image inspect --format '{{.Id}}' qemu-android-jb-native > "$REPORT/builder-image-id.txt"
docker run --rm --network none qemu-android-jb-native dpkg-query -W > "$REPORT/builder-packages.txt"
docker run --rm --network none \
  -e GITHUB_ACTIONS=true \
  --mount "type=bind,src=$WORK/src,dst=/src" \
  --mount "type=bind,src=$WORK/out,dst=/out" \
  --mount "type=bind,src=$ROOT/android/jellybean,dst=/port,readonly" \
  qemu-android-jb-native bash /port/build-in-container.sh
python3 "$ROOT/android/jellybean/package-boot.py"
# Retain exact native library/Android-init source plus downstream build changes.
tar --exclude=.git -C "$WORK/src" -czf "$REPORT/native-corresponding-sources.tar.gz" \
  build bionic system/core
cp "$ROOT/android/jellybean/init.rc" "$REPORT/init.rc"
cp "$ROOT/android/jellybean/Dockerfile" "$REPORT/builder.Dockerfile"
