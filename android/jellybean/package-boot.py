#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
import gzip
import hashlib
import json
import os
import pathlib
import struct
import sys
if os.environ.get('GITHUB_ACTIONS') != 'true': sys.exit('Guest packaging runs in Actions')
root = pathlib.Path(__file__).resolve().parents[2]
product = root / 'android/.aosp-userspace/out/target/product/generic/root'
report = root / 'android/reports/aosp-userspace'
init = (product / 'init').read_bytes()
probe = (product / 'sbin/qemu_android_native_probe').read_bytes()
for name, data in [('init', init), ('native_probe', probe)]:
    if data[:6] != b'\x7fELF\x01\x01' or struct.unpack_from('<H', data, 18)[0] != 40:
        raise ValueError('Expected genuine ELF32 ARM target: ' + name)
    # Reject dynamically linked executables: this tiny ramdisk has no linker.
    phoff = struct.unpack_from('<I', data, 28)[0]
    size, count = struct.unpack_from('<HH', data, 42)
    if size < 32 or phoff + size * count > len(data): raise ValueError('Invalid ELF headers')
    if any(struct.unpack_from('<I', data, phoff + i * size)[0] == 3 for i in range(count)):
        raise ValueError('Native boot target must be statically linked')
entries = [('dev', 0o040755, b''), ('dev/console', 0o020600, b''), ('proc', 0o040755, b''),
           ('sys', 0o040755, b''), ('sbin', 0o040755, b''), ('init', 0o100755, init),
           ('sbin/ueventd', 0o120777, b'../init'), ('sbin/qemu_android_native_probe', 0o100755, probe),
           ('init.rc', 0o100644, (root / 'android/jellybean/init.rc').read_bytes()),
           ('ueventd.rc', 0o100644, b'# Kernel default device permissions for native bring-up only\n'),
           ('default.prop', 0o100644, b'ro.secure=1\nro.debuggable=0\nro.hardware=qemu_android_native\n'),
           ('TRAILER!!!', 0, b'')]
archive = bytearray()
for inode, (name, mode, data) in enumerate(entries, 1):
    encoded = name.encode() + b'\0'
    fields = [inode, mode, 0, 0, 1, 0, len(data), 0, 0, 5 if name == 'dev/console' else 0, 1 if name == 'dev/console' else 0, len(encoded), 0]
    archive += b'070701' + ''.join(f'{v:08x}' for v in fields).encode() + encoded
    archive += b'\0' * (-len(archive) % 4); archive += data; archive += b'\0' * (-len(archive) % 4)
initrd = root / 'android/.aosp-userspace/native-initramfs.cpio.gz'
initrd.write_bytes(gzip.compress(bytes(archive), mtime=0))
(report / 'native-binaries.json').write_text(json.dumps({'init_sha256': hashlib.sha256(init).hexdigest(), 'native_probe_sha256': hashlib.sha256(probe).hexdigest(), 'initramfs_sha256': hashlib.sha256(initrd.read_bytes()).hexdigest(), 'android_framework_built': False, 'phone_guest_bundle': False}, indent=2) + '\n')
