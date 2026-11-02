#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Fetch exact dependency commits and explicitly declared pinned submodules."""
import json
import os
import pathlib
import re
import subprocess
import sys


def git(directory, *args):
    return subprocess.check_output(["git", "-C", str(directory), *args], text=True).strip()


def checked_path(value):
    path = pathlib.PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts or not path.parts:
        raise ValueError(f"Unsafe dependency path: {value}")
    return path


def checked_sha(value):
    if not re.fullmatch(r"[0-9a-f]{40}", value):
        raise ValueError("Dependency must specify a full commit SHA")
    return value


def fetch_dependency(dep, dest):
    pin = checked_sha(dep["commit"])
    subprocess.run(["git", "init", str(dest)], check=True, stdout=subprocess.DEVNULL)
    # A named origin is essential: relative .gitmodules URLs resolve against it.
    # Also repair the URL in restored source caches before fetching.
    git(dest, "config", "--local", "remote.origin.url", dep["repository"])
    git(dest, "fetch", "--no-tags", "--depth=1", "origin", pin)
    git(dest, "checkout", "--detach", "FETCH_HEAD")
    if git(dest, "rev-parse", "HEAD") != pin:
        raise RuntimeError(f"Dependency SHA mismatch: {dep['name']}")
    for sub in dep.get("submodules", []):
        path = str(checked_path(sub["path"]))
        sub_pin = checked_sha(sub["commit"])
        entry = git(dest, "ls-tree", "HEAD", "--", path).split()
        if len(entry) != 4 or entry[:3] != ["160000", "commit", sub_pin]:
            raise RuntimeError(f"Pinned submodule disagrees with parent gitlink: {path}")
        git(dest, "submodule", "init", "--", path)
        git(dest, "submodule", "sync", "--", path)
        # This project declares the submodule section name equal to its path.
        url = git(dest, "config", "--get", f"submodule.{path}.url")
        if url != sub["repository"]:
            raise RuntimeError(f"Unexpected submodule repository for {path}: {url}")
        git(dest, "submodule", "update", "--init", "--depth=1", "--", path)
        if git(dest / path, "rev-parse", "HEAD") != sub_pin:
            raise RuntimeError(f"Submodule SHA mismatch: {path}")


def main():
    if os.environ.get("GITHUB_ACTIONS") != "true":
        sys.exit("Dependency retrieval is CI-only")
    root = pathlib.Path(__file__).resolve().parents[2]
    out = pathlib.Path(sys.argv[1])
    for dep in json.loads((root / "android/ci/native-deps.json").read_text()):
        fetch_dependency(dep, out / checked_path(dep["name"]))


if __name__ == "__main__":
    main()
