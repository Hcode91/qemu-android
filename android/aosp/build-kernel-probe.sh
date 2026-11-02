#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-or-later
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
source "$ROOT/android/ci/guard.sh"
WORK="$ROOT/android/.aosp-kernel"
REPORT="$ROOT/android/reports/aosp-kernel"
mkdir -p "$WORK" "$REPORT"
exec > >(tee "$REPORT/build.log") 2>&1
python3 - "$ROOT" "$WORK" <<'PY'
import importlib.util, json, pathlib, sys
root, work = map(pathlib.Path, sys.argv[1:])
spec = importlib.util.spec_from_file_location('fetch', root / 'android/ci/fetch-deps.py')
fetch = importlib.util.module_from_spec(spec); spec.loader.exec_module(fetch)
lock = json.loads((root / 'android/aosp/sources.json').read_text())
for name in ['kernel', 'compiler']:
    dep = dict(lock[name], name=name)
    fetch.fetch_dependency(dep, work / name)
PY
export ARCH=arm
export CROSS_COMPILE="$WORK/compiler/bin/arm-eabi-"
export HOSTCFLAGS='-O2 -fcommon'
"${CROSS_COMPILE}gcc" --version > "$REPORT/compiler-version.txt"
make -C "$WORK/kernel" O="$WORK/output" vexpress_defconfig
"$WORK/kernel/scripts/kconfig/merge_config.sh" -m -O "$WORK/output" \
  "$WORK/output/.config" "$ROOT/android/aosp/kernel.fragment"
make -C "$WORK/kernel" O="$WORK/output" olddefconfig
python3 - "$ROOT" "$WORK" <<'PY'
import pathlib, sys
root, work = map(pathlib.Path, sys.argv[1:])
lines = set((work / 'output/.config').read_text().splitlines())
required = (root / 'android/aosp/kernel.fragment').read_text().splitlines()
missing = [line for line in required if line and line not in lines]
if missing:
    raise SystemExit('Required guest kernel options did not resolve: ' + ', '.join(missing))
PY
make -C "$WORK/kernel" O="$WORK/output" -j2 zImage dtbs
"${CROSS_COMPILE}gcc" -O2 -marm -march=armv7-a -fomit-frame-pointer -ffreestanding \
  -fno-stack-protector -fno-unwind-tables -fno-asynchronous-unwind-tables \
  -nostdlib -static -Wl,-e,_start -Wall -Wextra -Werror \
  "$ROOT/android/aosp/kernel-probe.c" -o "$WORK/kernel-probe"
"${CROSS_COMPILE}readelf" -h -l "$WORK/kernel-probe" > "$REPORT/probe-elf.txt"
python3 "$ROOT/android/aosp/probe-initramfs.py" "$WORK/kernel-probe" "$WORK/probe.cpio.gz"
cp "$WORK/output/.config" "$REPORT/kernel.config"
cp "$ROOT/android/aosp/sources.json" "$REPORT/sources.json"
git -C "$WORK/kernel" archive --format=tar.gz --output="$REPORT/kernel-corresponding-source.tar.gz" HEAD
tar -C "$ROOT" -czf "$REPORT/probe-source.tar.gz" android/aosp
# Keep this as a host-only probe: it is neither an Android system image nor a phone VM preset.
python3 "$ROOT/android/aosp/run-kernel-probe.py" \
  "$WORK/output/arch/arm/boot/zImage" \
  "$WORK/output/arch/arm/boot/dts/vexpress-v2p-ca9.dtb" \
  "$WORK/probe.cpio.gz" "$REPORT/boot"
