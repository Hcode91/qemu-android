#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Fail closed on OSV lookup errors or reported vulnerabilities. No automatic suppressions."""
import json
import os
import pathlib
import subprocess
import urllib.request
root = pathlib.Path(__file__).resolve().parents[2]
reports = root / "android/reports/security"
reports.mkdir(parents=True, exist_ok=True)
deps = json.loads((root / "android/ci/native-deps.json").read_text())
# Include declared source submodules, not just top-level dependency commits.
deps = [item for dep in deps for item in [dep, *dep.get("submodules", [])]]
deps += [{"name": "qemu", "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()}]
queries = [{"commit": d["commit"]} if "commit" in d else d["osv"] for d in deps]
# Gradle runtime and test dependencies exported by the build, including transitives.
resolved = root / "android/reports/gradle-dependencies.json"
if resolved.exists():
    maven = json.loads(resolved.read_text())
    queries += [{"package": {"ecosystem": "Maven", "name": d["name"]}, "version": d["version"]} for d in maven]
    deps += maven
request = urllib.request.Request("https://api.osv.dev/v1/querybatch", json.dumps({"queries": queries}).encode(), {"Content-Type": "application/json"})
try:
    with urllib.request.urlopen(request, timeout=90) as response:
        data = json.load(response)
    results = data["results"]
    assert len(results) == len(deps)
except Exception as exc:
    (reports / "osv-error.txt").write_text(str(exc))
    raise
report = [{"dependency": dep, "osv": result} for dep, result in zip(deps, results)]
(reports / "osv.json").write_text(json.dumps(report, indent=2) + "\n")
print(f"OSV checked {len(queries)} dependency coordinates/commits. Absence of findings is not a security guarantee.")
if not resolved.exists():
    print("Maven dependency scan unavailable: Gradle has not exported resolved dependencies.")
for dependency, result in zip(deps, results):
    ids = [finding["id"] for finding in result.get("vulns", [])]
    if ids:
        print(f"::error::{dependency['name']}: {', '.join(ids)}")
if any(result.get("vulns") for result in results):
    raise SystemExit("OSV findings require review; see reports/security/osv.json")
