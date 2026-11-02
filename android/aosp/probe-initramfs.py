#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Construct a tiny root-owned newc initramfs for the real ARM ABI probe in CI."""
import gzip
import os
import pathlib
import sys
if os.environ.get('GITHUB_ACTIONS') != 'true':
    sys.exit('Guest packaging runs only in Actions')
probe, target = map(pathlib.Path, sys.argv[1:])
archive = bytearray()
for inode, (name, mode, payload) in enumerate([
    ('dev', 0o040755, b''), ('dev/console', 0o020600, b''),
    ('init', 0o100755, probe.read_bytes()), ('TRAILER!!!', 0, b'')
], 1):
    encoded = name.encode() + b'\0'
    fields = [inode, mode, 0, 0, 1, 0, len(payload), 0, 0,
              5 if name == 'dev/console' else 0, 1 if name == 'dev/console' else 0, len(encoded), 0]
    archive += b'070701' + ''.join(f'{field:08x}' for field in fields).encode() + encoded
    archive += b'\0' * (-len(archive) % 4)
    archive += payload
    archive += b'\0' * (-len(archive) % 4)
target.write_bytes(gzip.compress(bytes(archive), mtime=0))
