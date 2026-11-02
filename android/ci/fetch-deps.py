#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
import json
import pathlib
import subprocess
import sys
if __import__("os").environ.get("GITHUB_ACTIONS") != "true":
    sys.exit("Dependency retrieval is CI-only")
root = pathlib.Path(__file__).resolve().parents[2]
out = pathlib.Path(sys.argv[1])
for dep in json.loads((root / "android/ci/native-deps.json").read_text()):
    dest = out / dep["name"]
    subprocess.run(["git", "init", str(dest)], check=True, stdout=subprocess.DEVNULL)
    subprocess.run(["git", "-C", str(dest), "fetch", "--depth=1", dep["repository"], dep["commit"]], check=True)
    subprocess.run(["git", "-C", str(dest), "checkout", "--detach", "FETCH_HEAD"], check=True)
    sha = subprocess.check_output(["git", "-C", str(dest), "rev-parse", "HEAD"], text=True).strip()
    if sha != dep["commit"]:
        sys.exit(f"Dependency SHA mismatch: {dep['name']}")
