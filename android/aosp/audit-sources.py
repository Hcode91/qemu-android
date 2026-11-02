#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Check pinned AOSP sources for guest-port requirements; never executes them."""
import base64
import hashlib
import json
import os
import pathlib
import re
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
if os.environ.get('GITHUB_ACTIONS') != 'true':
    sys.exit('Source audit runs in Actions')
root = pathlib.Path(__file__).resolve().parents[2]
lock = json.loads((root / 'android/aosp/sources.json').read_text())['aosp']
out = root / 'android/reports/aosp-source'
out.mkdir(parents=True, exist_ok=True)
requests = [
    ('manifest', 'platform/manifest', 'manifest_commit', 'default.xml'),
    ('board', 'platform/build', 'build_commit', 'target/board/generic/BoardConfig.mk'),
    ('init', 'platform/system/core', 'system_core_commit', 'rootdir/init.rc'),
    ('binder', 'platform/frameworks/native', 'frameworks_native_commit', 'libs/binder/ProcessState.cpp'),
    ('gralloc', 'platform/hardware/libhardware', 'hardware_libhardware_commit', 'modules/gralloc/framebuffer.cpp'),
]
texts, evidence = {}, []
for name, project, key, path in requests:
    sha = lock[key]
    if not re.fullmatch('[a-f0-9]{40}', sha):
        raise ValueError('AOSP source requires a full immutable commit')
    url = f'https://android.googlesource.com/{project}/+/{sha}/{path}?format=TEXT'
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=30) as response:
                encoded = response.read(2 * 1024 * 1024 + 1)
            if len(encoded) > 2 * 1024 * 1024:
                raise ValueError('Source audit response exceeds limit')
            data = base64.b64decode(b"".join(encoded.split()), validate=True)
            break
        except (urllib.error.URLError, TimeoutError):
            if attempt == 2: raise
            time.sleep(2)
    texts[name] = data.decode()
    (out / f'{name}.source.txt').write_bytes(data)
    evidence.append({'project': project, 'commit': sha, 'path': path, 'url': url, 'sha256': hashlib.sha256(data).hexdigest()})
manifest = ET.fromstring(texts['manifest'])
checks = {
    'armv7_target': 'TARGET_ARCH_VARIANT := armv7-a' in texts['board'],
    'kernel_must_be_supplied_separately': 'TARGET_NO_KERNEL := true' in texts['board'],
    'stock_init_expects_mtd_system': 'mount yaffs2 mtd@system /system' in texts['init'],
    'stock_init_imports_hardware_rc': 'import /init.${ro.hardware}.rc' in texts['init'],
    'userspace_checks_binder_version': 'BINDER_VERSION' in texts['binder'],
    'default_gralloc_supports_fbdev': '/dev/fb%u' in texts['gralloc'],
}
report = {'aosp_version': lock['version'], 'manifest_project_count': len(manifest.findall('project')),
          'sources': evidence, 'requirements_confirmed': checks,
          'full_aosp_checkout_locked': False, 'android_userspace_compiled': False,
          'android_guest_boot_verified': False}
(out / 'requirements.json').write_text(json.dumps(report, indent=2) + '\n')
if not all(checks.values()):
    raise SystemExit('Pinned source differs from the investigated port requirements; review requirements.json')
print('Pinned AOSP ARMv7 sources audited. This does not compile or boot Android.')
