#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
import json
import os
import pathlib
import subprocess
import sys
if os.environ.get('GITHUB_ACTIONS') != 'true':
    sys.exit('Guest tests run only in Actions')
kernel, dtb, initrd, report = map(pathlib.Path, sys.argv[1:])
report.mkdir(parents=True, exist_ok=True)
command = ['qemu-system-arm', '-M', 'vexpress-a9', '-cpu', 'cortex-a9', '-accel', 'tcg',
           '-m', '512', '-smp', '1', '-display', 'none', '-monitor', 'none', '-serial', 'stdio',
           '-nic', 'none', '-no-reboot', '-kernel', str(kernel), '-dtb', str(dtb), '-initrd', str(initrd),
           '-append', 'console=ttyAMA0 rdinit=/init panic=1']
(report / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
with (report / 'guest-serial.log').open('wb') as log:
    process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT)
    try:
        code = process.wait(timeout=120)
    finally:
        if process.poll() is None:
            process.kill(); process.wait(timeout=10)
text = (report / 'guest-serial.log').read_text(errors='replace')
required = ['Linux version 3.18.140', 'BINDER-PROTOCOL-7-OK', 'ASHMEM-SIZE-OK',
            'ANDROID-LOGGER-V1-OK', 'FRAMEBUFFER-DEVICE-OK', 'ANDROID-ABI-PROBE-PASS', 'Power down']
missing = [marker for marker in required if marker not in text]
result = {'exit_status': code, 'missing_markers': missing, 'kernel_abi_probe_passed': not missing and code == 0 and 'Kernel panic' not in text,
          'android_userspace_built': False, 'android_guest_boot_verified': False, 'real_device_verified': False}
(report / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
if not result['kernel_abi_probe_passed']:
    print(text[-6000:]); sys.exit('Kernel Android-ABI probe failed; inspect actual guest output')
print('Real ARM kernel ABI probe passed; Android userspace and phone boot remain unverified')
