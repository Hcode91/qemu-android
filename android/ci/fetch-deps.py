#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Fetch exact dependency commits and explicitly declared pinned submodules."""
import configparser
import hashlib
import io
import shutil
import tarfile
import tempfile
import urllib.request
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


def extract_archive(archive, data, dest):
    if hashlib.sha512(data).hexdigest() != archive["sha512"]:
        raise ValueError("Dependency archive hash mismatch")
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:xz") as source:
        entries = source.getmembers()
        if len(entries) > 10000:
            raise ValueError("Dependency archive exceeds extraction limits")
        by_path = {}
        for entry in entries:
            path = checked_path(entry.name)
            if (path.parts[0] != archive["root"] or path in by_path or entry.size < 0
                    or not (entry.isfile() or entry.isdir() or entry.issym())):
                raise ValueError(f"Unsafe dependency archive member: {entry.name!r}")
            by_path[path] = entry
        # Pixman's official tarball contains CI-config symlinks. Materialize only
        # direct, relative references to regular members as regular files. Never
        # extract a filesystem link, follow a link chain, or accept a hardlink.
        members = []
        for path, entry in by_path.items():
            if entry.issym():
                target_path = path.parent / checked_path(entry.linkname)
                target = by_path.get(target_path)
                if target is None or not target.isfile():
                    raise ValueError(f"Unsafe archive link target: {entry.name!r}")
                # Preserve the regular target's data offset/size, changing only
                # its output name. tarfile's data filter still applies below.
                entry = target.replace(name=entry.name, linkname="", deep=False)
            members.append(entry)
        if sum(e.size for e in members) > 128 * 1024 * 1024:
            raise ValueError("Dependency archive exceeds extraction limits")
        dest.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=dest.parent) as temp:
            source.extractall(temp, members=members, filter="data")
            if dest.exists():
                shutil.rmtree(dest)
            shutil.move(str(pathlib.Path(temp) / archive["root"]), dest)


def fetch_archive(dep, dest):
    archive = dep["archive"]
    if not archive["url"].startswith("https://"):
        raise ValueError("Dependency archive URL must use HTTPS")
    with urllib.request.urlopen(archive["url"], timeout=90) as response:
        data = response.read(32 * 1024 * 1024 + 1)
    if len(data) > 32 * 1024 * 1024:
        raise ValueError("Dependency archive download exceeds limit")
    extract_archive(archive, data, dest)


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



def fetch_manifest_entry(dep, out):
    destination = out / checked_path(dep.get("destination", dep["name"]))
    if "wrap" in dep:
        # A wrap fallback is an ordinary nested checkout, not a Git submodule.
        # Require metadata to agree with the parent source's pinned wrap file.
        wrap = dep["wrap"]
        parser = configparser.ConfigParser()
        with (out / checked_path(wrap["path"])).open() as source:
            parser.read_file(source)
        source = parser["wrap-git"]
        if (source["revision"] != checked_sha(dep["commit"])
                or source["url"] != wrap["url"]
                or source["directory"] != destination.name):
            raise RuntimeError(f"Pinned dependency disagrees with source wrap: {dep['name']}")
    if "archive" in dep:
        fetch_archive(dep, destination)
    else:
        fetch_dependency(dep, destination)


def main():
    if os.environ.get("GITHUB_ACTIONS") != "true":
        sys.exit("Dependency retrieval is CI-only")
    root = pathlib.Path(__file__).resolve().parents[2]
    out = pathlib.Path(sys.argv[1])
    for dep in json.loads((root / "android/ci/native-deps.json").read_text()):
        fetch_manifest_entry(dep, out)


if __name__ == "__main__":
    main()
