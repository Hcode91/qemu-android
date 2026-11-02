# SPDX-License-Identifier: GPL-2.0-or-later
"""CI-only Git fixture regression tests. No Android/native compilation."""
import hashlib
import io
import tarfile
import importlib.util
import os
import pathlib
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("fetch_deps", pathlib.Path(__file__).resolve().parents[1] / "fetch-deps.py")
fetch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fetch)


def git(directory, *args):
    return subprocess.check_output(["git", "-C", str(directory), *args], text=True, stderr=subprocess.PIPE).strip()


def commit(directory, message, stage_all=True):
    if stage_all:
        git(directory, "add", ".")
    git(directory, "-c", "user.name=CI Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-m", message)
    return git(directory, "rev-parse", "HEAD")


@unittest.skipUnless(os.environ.get("GITHUB_ACTIONS") == "true", "Dependency integration tests run in Actions")
class DependencyFetchTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        # Allow local remotes only for this offline fixture; preserve the worker env.
        self.old_protocol = os.environ.get("GIT_ALLOW_PROTOCOL")
        os.environ["GIT_ALLOW_PROTOCOL"] = "file"
        self.addCleanup(self.restore_protocol)
        mirror = self.root / "upstream/GNOME"
        mirror.mkdir(parents=True)
        child_work = self.root / "gvdb-work"
        child_work.mkdir()
        git(child_work, "init")
        (child_work / "meson.build").write_text("project('fixture', 'c')\n")
        self.child_sha = commit(child_work, "fixture submodule")
        git(self.root, "clone", "--bare", str(child_work), str(mirror / "gvdb.git"))
        parent_work = self.root / "glib-work"
        parent_work.mkdir()
        git(parent_work, "init")
        (parent_work / ".gitmodules").write_text('[submodule "subprojects/gvdb"]\n path = subprojects/gvdb\n url = ../../GNOME/gvdb.git\n')
        git(parent_work, "add", ".gitmodules")
        git(parent_work, "update-index", "--add", "--cacheinfo", f"160000,{self.child_sha},subprojects/gvdb")
        parent_sha = commit(parent_work, "fixture parent", stage_all=False)
        git(self.root, "clone", "--bare", str(parent_work), str(mirror / "glib.git"))
        self.dep = {
            "name": "glib", "repository": (mirror / "glib.git").as_uri(), "commit": parent_sha,
            "submodules": [{"name": "gvdb", "path": "subprojects/gvdb", "repository": (mirror / "gvdb.git").as_uri(), "commit": self.child_sha}]
        }
        self.dest = self.root / "cache/glib"

    def restore_protocol(self):
        if self.old_protocol is None:
            os.environ.pop("GIT_ALLOW_PROTOCOL", None)
        else:
            os.environ["GIT_ALLOW_PROTOCOL"] = self.old_protocol

    def test_relative_submodule_checkout_and_cached_url_repair(self):
        fetch.fetch_dependency(self.dep, self.dest)
        self.assertEqual(git(self.dest, "remote", "get-url", "origin"), self.dep["repository"])
        self.assertEqual(git(self.dest / "subprojects/gvdb", "rev-parse", "HEAD"), self.child_sha)
        # Reproduce malformed URL state from a source cache populated without origin.
        git(self.dest, "config", "remote.origin.url", "/missing/parent.git")
        git(self.dest, "config", "submodule.subprojects/gvdb.url", "/missing/gvdb.git")
        fetch.fetch_dependency(self.dep, self.dest)
        self.assertEqual(git(self.dest, "config", "submodule.subprojects/gvdb.url"), self.dep["submodules"][0]["repository"])

    def test_pinned_wrap_checkout_and_revision_mismatch(self):
        fetch.fetch_dependency(self.dep, self.dest)
        child = self.dep["submodules"][0]
        wrap_file = self.dest / "subprojects/proxy-fixture.wrap"
        wrap_file.write_text(f"[wrap-git]\ndirectory=proxy-fixture\nurl={child['repository']}\nrevision={self.child_sha}\n")
        dep = {
            "name": "proxy-fixture", "repository": child["repository"], "commit": self.child_sha,
            "destination": "glib/subprojects/proxy-fixture",
            "wrap": {"path": "glib/subprojects/proxy-fixture.wrap", "url": child["repository"]}
        }
        fetch.fetch_manifest_entry(dep, self.dest.parent)
        self.assertEqual(git(self.dest / "subprojects/proxy-fixture", "rev-parse", "HEAD"), self.child_sha)
        dep["commit"] = "0" * 40
        with self.assertRaisesRegex(RuntimeError, "source wrap"):
            fetch.fetch_manifest_entry(dep, self.dest.parent)

    def test_gitlink_mismatch_is_rejected(self):
        self.dep["submodules"][0]["commit"] = "0" * 40
        with self.assertRaisesRegex(RuntimeError, "gitlink"):
            fetch.fetch_dependency(self.dep, self.dest)

    def test_unsafe_submodule_path_is_rejected(self):
        self.dep["submodules"][0]["path"] = "../outside"
        with self.assertRaises(ValueError):
            fetch.fetch_dependency(self.dep, self.dest)


@unittest.skipUnless(os.environ.get("GITHUB_ACTIONS") == "true", "Source archive tests run in Actions")
class ArchiveFetchTest(unittest.TestCase):
    def archive(self, name, value=b"source"):
        stream = io.BytesIO()
        with tarfile.open(fileobj=stream, mode="w:xz") as tar:
            item = tarfile.TarInfo(name); item.size = len(value)
            tar.addfile(item, io.BytesIO(value))
        data = stream.getvalue()
        return {"root": "fixture-1", "sha512": hashlib.sha512(data).hexdigest()}, data

    def test_archive_hash_and_safe_extraction(self):
        archive, data = self.archive("fixture-1/source.c")
        with tempfile.TemporaryDirectory() as temp:
            dest = pathlib.Path(temp) / "source"
            fetch.extract_archive(archive, data, dest)
            self.assertEqual((dest / "source.c").read_bytes(), b"source")
            with self.assertRaises(ValueError):
                fetch.extract_archive(archive, data + b"changed", dest)

    def test_archive_path_traversal_is_rejected(self):
        archive, data = self.archive("fixture-1/../../outside")
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(ValueError):
                fetch.extract_archive(archive, data, pathlib.Path(temp) / "source")


if __name__ == "__main__":
    unittest.main()
