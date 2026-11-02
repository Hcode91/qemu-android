#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Host-side ARM virt boot harness. Run only in Actions with a Linux zImage,
initramfs and raw ext4 test disk. Does NOT certify Android backend execution.
The guest init script must implement the protocol in android/guest/init.
"""
import os
import pathlib
import subprocess
import sys
if os.environ.get("GITHUB_ACTIONS") != "true":
    sys.exit("Guest integration tests run through GitHub Actions only")
qemu, kernel, initrd, disk, report = sys.argv[1:]
out = pathlib.Path(report)
out.mkdir(parents=True, exist_ok=True)
for attempt, marker in [(1, "PERSIST-WRITTEN"), (2, "PERSIST-READ")]:
    command = [qemu, "-M", "virt", "-cpu", "cortex-a15", "-accel", "tcg", "-m", "256", "-smp", "1",
               "-no-user-config", "-nodefaults", "-nographic", "-monitor", "none", "-serial", "stdio", "-nic", "none",
               "-kernel", kernel, "-initrd", initrd, "-append", "console=ttyAMA0 rdinit=/init panic=1",
               "-drive", f"if=none,id=disk0,format=raw,file={disk}", "-device", "virtio-blk-device,drive=disk0"]
    try:
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=180)
    except subprocess.TimeoutExpired as exc:
        (out / f"boot-{attempt}.log").write_bytes(exc.stdout or b"")
        raise
    (out / f"boot-{attempt}.log").write_bytes(result.stdout)
    text = result.stdout.decode(errors="replace")
    assert result.returncode == 0, f"Guest run failed: {result.returncode}"
    for expected in ["Linux version", "QEMU-ANDROID-USERSPACE", marker, "QEMU-ANDROID-SHUTDOWN", "reboot: Power down"]:
        assert expected in text, f"Missing {expected}; inspect boot-{attempt}.log"
print("Two host-QEMU ARM Linux boots, userspace, raw disk persistence and poweroff verified; Android remains unverified")
