#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Checkout only the pinned source closure for AOSP's native bring-up mode."""
import concurrent.futures
import importlib.util
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
if os.environ.get('GITHUB_ACTIONS') != 'true':
    sys.exit('Guest source preparation runs in Actions')
root = pathlib.Path(__file__).resolve().parents[2]
src = root / 'android/.aosp-userspace/src'
report = root / 'android/reports/aosp-userspace'
src.mkdir(parents=True, exist_ok=True); report.mkdir(parents=True, exist_ok=True)
lock = json.loads((root / 'android/jellybean/project-lock.json').read_text())
selected = {
    'build', 'bionic', 'system/core', 'system/extras', 'frameworks/native', 'external/gcc-demangle',
    'external/mksh', 'external/yaffs2', 'external/zlib', 'external/libselinux',
    'external/e2fsprogs', 'hardware/libhardware', 'hardware/libhardware_legacy',
    'device/generic/goldfish', 'prebuilt', 'prebuilts/misc', 'prebuilts/tools',
    'prebuilts/gcc/linux-x86/arm/arm-linux-androideabi-4.6',
    'prebuilts/gcc/linux-x86/host/x86_64-linux-glibc2.7-4.6',
}
baseline = json.loads((root / 'android/aosp/sources.json').read_text())['aosp']
known = {'build': 'build_commit', 'system/core': 'system_core_commit',
         'frameworks/native': 'frameworks_native_commit', 'hardware/libhardware': 'hardware_libhardware_commit'}
by_path = {p['path']: p for p in lock['projects']}
if len(by_path) != len(lock['projects']) or lock['manifest_commit'] != baseline['manifest_commit']:
    raise ValueError('Manifest lock differs from the investigated release')
for path, key in known.items():
    if by_path[path]['commit'] != baseline[key]: raise ValueError('Investigated project pin changed: ' + path)
projects = [p for p in lock['projects'] if p['path'] in selected]
if {p['path'] for p in projects} != selected:
    raise ValueError('Native source closure is absent from the pinned manifest')
for p in lock['projects']:
    for value in [p['path'], p['name']]:
        path = pathlib.PurePosixPath(value)
        if path.is_absolute() or '..' in path.parts or not re.fullmatch(r'[a-zA-Z0-9_./+-]+', value):
            raise ValueError('Unsafe manifest path')
    if not re.fullmatch(r'[a-f0-9]{40}', p['commit']): raise ValueError('Unpinned project')
spec = importlib.util.spec_from_file_location('fetch', root / 'android/ci/fetch-deps.py')
fetch = importlib.util.module_from_spec(spec); spec.loader.exec_module(fetch)
def checkout(project):
    dest = src / project['path']
    fetch.fetch_dependency({'name': project['path'], 'repository': 'https://android.googlesource.com/' + project['name'], 'commit': project['commit']}, dest)
    # This is a dedicated disposable CI source cache, never the user's checkout.
    subprocess.run(['git', '-C', str(dest), 'reset', '--hard', project['commit']], check=True, stdout=subprocess.DEVNULL)
    subprocess.run(['git', '-C', str(dest), 'clean', '-fdx'], check=True, stdout=subprocess.DEVNULL)
    print('Checked pinned source:', project['path'], project['commit'], flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    list(pool.map(checkout, projects))
for project in projects:
    for copy in project.get('copyfiles', []):
        source = src / project['path'] / fetch.checked_path(copy['src'])
        target = src / fetch.checked_path(copy['dest'])
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
# Product configuration imports heap settings from frameworks/native even for
# the C-only target. Check literal, mandatory inherit-product paths before the
# expensive container build. This is a static preflight, not a Make evaluator.
queue = list((src / 'build/target/product').glob('*.mk'))
visited, includes, missing = set(), [], []
while queue:
    makefile = queue.pop()
    if makefile in visited: continue
    visited.add(makefile)
    text = makefile.read_text()
    text = '\n'.join(line for line in text.splitlines() if not line.lstrip().startswith('#'))
    text = text.replace('$(SRC_TARGET_DIR)', 'build/target')
    text = text.replace('$(LOCAL_PATH)', str(makefile.parent.relative_to(src)))
    for value in re.findall(r'\$\(\s*call\s+inherit-product\s*,\s*([a-zA-Z0-9_./+-]+)\s*\)', text):
        target = src / fetch.checked_path(value)
        exists = target.is_file()
        includes.append({'from': str(makefile.relative_to(src)), 'requires': value, 'present': exists})
        if exists: queue.append(target)
        else: missing.append(value)
(report / 'product-includes.json').write_text(json.dumps({'scope': 'Literal mandatory inherit-product references; optional and dynamic expressions remain Make checks', 'includes': includes, 'missing': sorted(set(missing))}, indent=2) + '\n')
if missing:
    raise ValueError('Native checkout lacks mandatory product configuration: ' + ', '.join(sorted(set(missing))))
# Genuine OpenJDK 6 is used for this C-only target. Preserve Java/javac 1.6
# checks, removing only the old vendor rejection. No version check is bypassed.
build = src / 'build/core/main.mk'
text = build.read_text()
old = 'ifneq ($(shell java -version 2>&1 | grep -i openjdk),)\njava_version :=\nendif\n'
if text.count(old) != 1: raise ValueError('Pinned Java vendor check changed')
build.write_text(text.replace(old, '# Native bring-up accepts OpenJDK 6; version 1.6 checks remain.\n'))
(report / 'build-compatibility.patch').write_bytes(subprocess.check_output(['git', '-C', str(src / 'build'), 'diff', '--', 'core/main.mk']))
overlay = src / 'system/core/qemu_android_native_probe'
overlay.mkdir()
for name in ['Android.mk', 'native-probe.c']:
    shutil.copyfile(root / 'android/jellybean' / name, overlay / name)
(report / 'checkout.json').write_text(json.dumps({'manifest_commit': lock['manifest_commit'], 'locked_projects': len(lock['projects']), 'checked_out': projects, 'mode': 'BUILD_TINY_ANDROID=true; native targets only'}, indent=2) + '\n')
shutil.copyfile(root / 'android/jellybean/project-lock.json', report / 'project-lock.json')
