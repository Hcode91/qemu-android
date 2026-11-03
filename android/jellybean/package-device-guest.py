#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Expose the exact host-tested native guest with its corresponding sources."""
import hashlib
import json
import os
import pathlib
import shutil
import sys

if os.environ.get('GITHUB_ACTIONS') != 'true':
    sys.exit('Guest packaging runs in Actions')
root = pathlib.Path(__file__).resolve().parents[2]
report = root / 'android/reports/aosp-userspace'
kernel_report = root / 'android/reports/aosp-kernel'
result = json.loads((report / 'boot/result.json').read_text())
if (result.get('aosp_native_userspace_passed') is not True or result.get('missing') != []
        or result.get('qemu_exit_status') != 0 or result.get('timed_out') is not False):
    raise ValueError('Only the completely passing native guest may be packaged')
kernel = root / 'android/.aosp-kernel/output/arch/arm/boot'
inputs = {'kernel': kernel / 'zImage', 'dtb': kernel / 'dts/vexpress-v2p-ca9.dtb',
          'initrd': root / 'android/.aosp-userspace/native-initramfs.cpio.gz'}
command = json.loads((report / 'boot/command.json').read_text())
for name, flag in [('kernel', '-kernel'), ('dtb', '-dtb'), ('initrd', '-initrd')]:
    if command[command.index(flag) + 1] != str(inputs[name]):
        raise ValueError('Bundle must contain the exact files used by the boot test')
native_hashes = json.loads((report / 'native-binaries.json').read_text())
if hashlib.sha256(inputs['initrd'].read_bytes()).hexdigest() != native_hashes['initramfs_sha256']:
    raise ValueError('Initramfs differs from compiled native evidence')
destination = root / 'android/dist/aosp-native-guest'
destination.mkdir(parents=True, exist_ok=False)
names = {'kernel': 'zImage', 'dtb': 'vexpress-v2p-ca9.dtb', 'initrd': 'native-initramfs.cpio.gz'}
manifest = {'format': 1, 'source_commit': os.environ['GITHUB_SHA'],
            'run': 'https://github.com/' + os.environ['GITHUB_REPOSITORY'] + '/actions/runs/' + os.environ['GITHUB_RUN_ID'],
            'profile': 'AOSP_NATIVE', 'machine': 'vexpress-a9', 'cpu': 'cortex-a9', 'memory_mib': 512, 'cpus': 1,
            'host_native_boot_verified': True, 'phone_verified': False,
            'android_framework_booted': False, 'android_ui_verified': False, 'images': {}}
checksums = []
for slot, source in inputs.items():
    target = destination / names[slot]
    shutil.copyfile(source, target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    if digest != hashlib.sha256(source.read_bytes()).hexdigest():
        raise ValueError('Guest file changed during copy')
    manifest['images'][slot] = {'file': target.name, 'sha256': digest, 'bytes': target.stat().st_size}
    checksums.append(f'{digest}  {target.name}')
sources = destination / 'sources'; sources.mkdir()
for source in [kernel_report / 'kernel-corresponding-source.tar.gz', kernel_report / 'kernel.config',
               kernel_report / 'sources.json', report / 'native-corresponding-sources.tar.gz',
               report / 'build-compatibility.patch', report / 'project-lock.json']:
    shutil.copyfile(source, sources / source.name)
    checksums.append(f'{hashlib.sha256(source.read_bytes()).hexdigest()}  sources/{source.name}')
# Preserve the downstream probe/config/packaging sources too. The repository
# commit in manifest.json supplies the complete build scripts at the exact pin.
shutil.copytree(root / 'android/jellybean', sources / 'downstream',
                ignore=shutil.ignore_patterns('__pycache__', '*.pyc'))
shutil.copyfile(report / 'boot/result.json', destination / 'host-boot-result.json')
shutil.copyfile(root / 'android/jellybean/DEVICE_TEST.md', destination / 'README.md')
(destination / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
(destination / 'SHA256SUMS').write_text('\n'.join(checksums) + '\n')
(report / 'device-bundle.json').write_text(json.dumps(manifest, indent=2) + '\n')
print('Packaged the exact host-tested early-native guest with corresponding sources. Phone boot remains unverified.')
