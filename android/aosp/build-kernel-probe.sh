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
# Linux 3.18 assigns HOSTCFLAGS itself, overriding an exported environment value.
# Preserve its exact default flags and pass the compatibility addition on make's
# command line so recursive Kbuild also uses it for both DTC translation units.
HOST_TOOL_FLAGS='-Wall -Wmissing-prototypes -Wstrict-prototypes -O2 -fomit-frame-pointer -std=gnu89 -fcommon'
kernel_make() {
  make -C "$WORK/kernel" O="$WORK/output" "HOSTCFLAGS=$HOST_TOOL_FLAGS" "$@"
}
"${CROSS_COMPILE}gcc" --version > "$REPORT/compiler-version.txt"
kernel_make vexpress_defconfig
"$WORK/kernel/scripts/kconfig/merge_config.sh" -m -O "$WORK/output" \
  "$WORK/output/.config" "$ROOT/android/aosp/kernel.fragment"
kernel_make olddefconfig
python3 - "$ROOT" "$WORK" <<'PY'
import pathlib, sys
root, work = map(pathlib.Path, sys.argv[1:])
lines = set((work / 'output/.config').read_text().splitlines())
required = (root / 'android/aosp/kernel.fragment').read_text().splitlines()
missing = [line for line in required if line and line not in lines]
if missing:
    raise SystemExit('Required guest kernel options did not resolve: ' + ', '.join(missing))
PY
# Fail early on host-tool incompatibilities and retain their real command lines.
kernel_make -j2 V=1 scripts
[[ -x "$WORK/output/scripts/dtc/dtc" ]] || { echo 'Built host DTC missing' >&2; exit 1; }
"$WORK/output/scripts/dtc/dtc" --version > "$REPORT/dtc-version.txt"
kernel_make -j2 zImage dtbs
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
