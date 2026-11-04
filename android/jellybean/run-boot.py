#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
import json
import os
import pathlib
import subprocess
import sys
if os.environ.get('GITHUB_ACTIONS') != 'true': sys.exit('Guest testing runs in Actions')
root = pathlib.Path(__file__).resolve().parents[2]
report = root / 'android/reports/aosp-userspace/boot'; report.mkdir(parents=True, exist_ok=True)
kernel = root / 'android/.aosp-kernel/output/arch/arm/boot'
command = ['qemu-system-arm', '-no-user-config', '-nodefaults', '-M', 'vexpress-a9,audiodev=aosp-silent',
           '-audiodev', 'none,id=aosp-silent', '-cpu', 'cortex-a9',
           '-accel', 'tcg,thread=single,split-wx=on', '-m', '512', '-smp', '1',
           '-display', 'none', '-monitor', 'none', '-serial', 'stdio', '-nic', 'none', '-no-reboot',
           '-kernel', str(kernel / 'zImage'), '-dtb', str(kernel / 'dts/vexpress-v2p-ca9.dtb'),
           '-initrd', str(root / 'android/.aosp-userspace/native-initramfs.cpio.gz'),
           '-append', 'console=ttyAMA0 rdinit=/init panic=1 androidboot.console=ttyAMA0 androidboot.hardware=qemu_android_native']
(report / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
with (report / 'guest-serial.log').open('wb') as log:
    process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT)
    timed_out = False
    try:
        code = process.wait(timeout=180)
    except subprocess.TimeoutExpired:
        timed_out = True
    finally:
        if process.poll() is None: process.kill(); process.wait(timeout=10)
    code = process.returncode
text = (report / 'guest-serial.log').read_text(errors='replace')
required = ['QEMU-AOSP-INIT-EARLY', 'QEMU-AOSP-INIT-ACTIONS', 'QEMU-AOSP-PROPERTY-TRIGGER',
            'AOSP-BIONIC-SERVICE', 'AOSP-KMSG-DEVICE-OK', 'AOSP-PROPERTY-READ-OK', 'AOSP-BIONIC-BINDER-OK',
            'AOSP-BIONIC-ASHMEM-OK', 'AOSP-LIBLOG-WRITE-OK', 'AOSP-NATIVE-USERSPACE-PASS', 'Power down']
missing = [marker for marker in required if marker not in text]
passed = code == 0 and not timed_out and not missing and 'Kernel panic' not in text and 'AOSP-NATIVE-FAIL' not in text
result = {'aosp_native_userspace_passed': passed, 'missing': missing, 'qemu_exit_status': code,
          'timed_out': timed_out, 'android_framework_booted': False, 'android_ui_verified': False, 'phone_verified': False}
(report / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))
if not passed:
    print(text[-9000:]); sys.exit('Genuine AOSP native boot failed; inspect guest output')
print('Genuine AOSP init/Bionic service, property reads, Binder, ashmem, liblog and poweroff passed. Android framework and phone boot remain pending.')
