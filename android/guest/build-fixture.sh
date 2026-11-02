#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-or-later
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
source "$ROOT/android/ci/guard.sh"
WORK="$ROOT/android/.guest"
REPORT="$ROOT/android/reports/guest"
DIST="$ROOT/android/guest-dist"
mkdir -p "$WORK" "$REPORT" "$DIST"
exec > >(tee "$REPORT/build.log") 2>&1
BR_SHA=$(python3 -c 'import json; print(json.load(open("android/guest/sources.json"))["buildroot"]["commit"])')
git init "$WORK/buildroot"
git -C "$WORK/buildroot" fetch --no-tags --depth=1 https://github.com/buildroot/buildroot.git "$BR_SHA"
git -C "$WORK/buildroot" checkout --detach FETCH_HEAD
[[ "$(git -C "$WORK/buildroot" rev-parse HEAD)" == "$BR_SHA" ]]
python3 - "$ROOT" "$WORK" <<'EOF'
from pathlib import Path
import sys
root, work = map(Path, sys.argv[1:])
guest_dir = str(root / "android/guest")
assert not any(c in guest_dir for c in ['"', '\n'])
config = (root / "android/guest/virt_defconfig.in").read_text().replace("@GUEST_DIR@", guest_dir)
(work / "virt_defconfig").write_text(config)
EOF
export BR2_DL_DIR="$ROOT/android/.guest-downloads"
make -C "$WORK/buildroot" O="$WORK/output" BR2_DEFCONFIG="$WORK/virt_defconfig" defconfig
make -C "$WORK/buildroot" O="$WORK/output" -j2
# Keep exact configuration, package metadata and corresponding source/licence files.
make -C "$WORK/buildroot" O="$WORK/output" legal-info
make --no-print-directory -s -C "$WORK/buildroot" O="$WORK/output" show-info > "$REPORT/buildroot-packages.json"
cp "$WORK/output/.config" "$REPORT/buildroot.config"
cp "$WORK/output/build/linux-6.18.7/.config" "$REPORT/linux.config"
find "$WORK/output/build" -maxdepth 2 -path '*/busybox-*/.config' -exec cp {} "$REPORT/busybox.config" \;
tar -C "$WORK/output" -czf "$REPORT/guest-corresponding-sources.tar.gz" legal-info
git -C "$WORK/buildroot" archive --format=tar.gz --output="$REPORT/buildroot-source.tar.gz" HEAD
cp "$WORK/output/images/zImage" "$DIST/kernel.zImage"
cp "$WORK/output/images/rootfs.cpio.gz" "$DIST/initramfs.cpio.gz"
# Deliver a pristine disk; test boots write only to a separate copy.
truncate -s 64M "$DIST/disk.raw"
mkfs.ext4 -F -L QEMUAndroid "$DIST/disk.raw"
cp "$DIST/disk.raw" "$WORK/test-disk.raw"
python3 "$ROOT/android/ci/boot-test.py" "$(command -v qemu-system-arm)" \
  "$DIST/kernel.zImage" "$DIST/initramfs.cpio.gz" "$WORK/test-disk.raw" "$REPORT/boot"
cp "$ROOT/android/guest/README.md" "$DIST/README.md"
cp "$ROOT/android/guest/sources.json" "$DIST/sources.json"
(cd "$DIST" && sha256sum kernel.zImage initramfs.cpio.gz disk.raw > SHA256SUMS)
python3 - "$ROOT" <<'EOF'
from pathlib import Path
import json, os, subprocess, sys
root = Path(sys.argv[1])
report = {
  "source_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
  "run_url": f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
  "sources": json.loads((root / "android/guest/sources.json").read_text()),
  "host_qemu_version": subprocess.check_output(["qemu-system-arm", "--version"], text=True).splitlines()[0],
  "host_fixture_boots_verified": 2,
  "android_guest_boot_verified": False,
  "real_device_vm_creation": "Owner reported success on Galaxy S23; guest execution not yet tested",
  "security_scope": "Buildroot download hashes and source/package evidence retained; no full guest vulnerability clearance claimed"
}
(root / "android/reports/guest/provenance.json").write_text(json.dumps(report, indent=2) + "\n")
EOF
