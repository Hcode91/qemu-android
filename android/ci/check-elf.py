#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
import re
import subprocess
import sys
binary, readelf = sys.argv[1:]
text = subprocess.check_output([readelf, "-W", "-h", "-l", "-d", binary], text=True)
assert "AArch64" in text and re.search(r"Type:\s+DYN", text), "Expected ARM64 PIE"
assert "/system/bin/linker64" in text, "Not an Android executable"
assert "TEXTREL" not in text, "Text relocations forbidden"
assert "BIND_NOW" in text and "GNU_RELRO" in text, "Missing relocation hardening"
allowed = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so"}
needed = set(re.findall(r"Shared library: \[(.*?)\]", text))
assert needed <= allowed, f"Unpackaged dependencies: {needed - allowed}"
loads = [line for line in text.splitlines() if line.strip().startswith("LOAD ")]
assert loads and all(int(line.split()[-1], 16) >= 16384 for line in loads), "Requires 16 KiB ELF alignment"
for line in text.splitlines():
    if line.strip().startswith("GNU_STACK"):
        assert " E " not in line, "Executable host stack forbidden"
print("Android ARM64 PIE, static dependency closure, hardening and 16 KiB alignment checked")
