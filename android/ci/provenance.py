#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
import hashlib
import json
import os
import pathlib
import subprocess
root = pathlib.Path(__file__).resolve().parents[2]
backend = root / "android/app/src/main/jniLibs/arm64-v8a/libqemu-system-arm.so"
report = {
    "qemu_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
    "qemu_version": (root / "VERSION").read_text().strip(),
    "backend_sha256": hashlib.sha256(backend.read_bytes()).hexdigest(),
    "run_url": f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    "ndk": "28.2.13676358", "api": 28, "abi": "arm64-v8a",
    "guest_boot_verified": False, "real_device_verified": False,
    "dependencies": json.loads((root / "android/ci/native-deps.json").read_text())
}
(root / "android/reports/native/provenance.json").write_text(json.dumps(report, indent=2) + "\n")
