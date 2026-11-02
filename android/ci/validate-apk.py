#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
import hashlib
import json
import pathlib
import re
import sys
import zipfile
import xml.etree.ElementTree as ET
apk, manifest, kind = sys.argv[1:]
assert kind in {"debug", "unsigned", "release"}
ns = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(manifest).getroot()
assert not root.findall("uses-permission"), "Phase one requires no Android permissions"
app = root.find("application")
assert app is not None
assert app.get(ns + "allowBackup") == "false"
assert app.get(ns + "usesCleartextTraffic") == "false"
assert app.get(ns + "extractNativeLibs") == "true"
assert (app.get(ns + "debuggable") == "true") == (kind == "debug"), "Wrong signing/build variant"
assert root.get("package") == ("org.qemu.android.dev" if kind == "debug" else "org.qemu.android")
for service in app.findall("service"):
    assert service.get(ns + "exported") == "false", "Exported VM service"
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None, "APK ZIP integrity failure"
    native = z.read("lib/arm64-v8a/libqemu-system-arm.so")
    assert len(native) > 1_000_000 and native[:4] == b"\x7fELF", "Missing genuine backend"
    assert int.from_bytes(native[18:20], "little") == 183, "Wrong native architecture"
    original = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/jniLibs/arm64-v8a/libqemu-system-arm.so"
    if original.exists():
        assert native == original.read_bytes(), "Packager altered native backend"
    source = pathlib.Path(__file__).resolve().parents[2] / "pc-bios/keymaps"
    maps = [p for p in source.iterdir() if p.is_file() and not p.is_symlink() and re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,31}", p.name)]
    assert any(p.name == "en-us" for p in maps), "Upstream default keymap missing"
    evidence = []
    for path in sorted(maps):
        entry = "assets/qemu/keymaps/" + path.name
        data = z.read(entry)  # Missing runtime assets must fail APK validation.
        assert data and data == path.read_bytes(), f"Missing/altered QEMU runtime data: {entry}"
        evidence.append({"asset": entry, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
    report = pathlib.Path(__file__).resolve().parents[1] / f"reports/apk-{kind}"
    report.mkdir(parents=True, exist_ok=True)
    (report / "runtime-data.json").write_text(json.dumps(evidence, indent=2) + "\n")
print(f"Manifest, permissions, variant, ZIP integrity, backend and {len(maps)} exact runtime keymaps verified")
