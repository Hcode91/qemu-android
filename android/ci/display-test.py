#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Real guest framebuffer/input check on host QEMU. Not Android renderer evidence."""
import json
import os
import pathlib
import socket
import struct
import subprocess
import sys
import time

if os.environ.get('GITHUB_ACTIONS') != 'true':
    sys.exit('Guest tests run only in Actions')
qemu, kernel, initrd, disk, target = sys.argv[1:]
report = pathlib.Path(target).resolve()
report.mkdir(parents=True, exist_ok=True)
serial = report / 'guest-serial.log'
vnc_path = report / 'vnc.sock'
qmp_path = report / 'qmp.sock'

def wait_for(text, timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if text in (serial.read_text(errors='replace') if serial.exists() else ''):
            return
        if process.poll() is not None:
            raise RuntimeError(f'QEMU exited before {text}')
        time.sleep(0.1)
    raise TimeoutError(f'Guest did not report {text}')

def exact(sock, size):
    result = bytearray()
    while len(result) < size:
        block = sock.recv(size - len(result))
        if not block:
            raise EOFError('VNC disconnected')
        result += block
    return bytes(result)

with (report / 'qemu.log').open('wb') as host:
    command = [qemu, '-M', 'virt', '-cpu', 'cortex-a15', '-accel', 'tcg', '-m', '512', '-smp', '2',
               '-no-user-config', '-nodefaults', '-display', 'none', '-monitor', 'none', '-nic', 'none',
               '-serial', f'file:{serial}', '-qmp', f'unix:{qmp_path},server=on,wait=off',
               '-vnc', f'unix:{vnc_path}', '-device', 'virtio-gpu-device', '-device', 'virtio-keyboard-device',
               '-device', 'virtio-tablet-device', '-kernel', kernel, '-initrd', initrd,
               '-append', 'console=tty0 console=ttyAMA0 rdinit=/init panic=1 qemu_android.display_test=1',
               '-drive', f'if=none,id=disk0,format=raw,file={disk}', '-device', 'virtio-blk-device,drive=disk0']
    process = subprocess.Popen(command, stdout=host, stderr=subprocess.STDOUT)
    try:
        wait_for('DISPLAY-READY')
        with socket.socket(socket.AF_UNIX) as client:
            client.settimeout(15)
            client.connect(str(vnc_path))
            assert exact(client, 12) == b'RFB 003.008\n'
            client.sendall(b'RFB 003.008\n')
            count = exact(client, 1)[0]
            assert 1 <= count <= 32 and 1 in exact(client, count)
            client.sendall(b'\x01'); assert exact(client, 4) == b'\0\0\0\0'
            client.sendall(b'\x01')
            width, height = struct.unpack('>HH', exact(client, 4))
            assert 0 < width <= 2048 and 0 < height <= 2048
            exact(client, 16)
            name_size = struct.unpack('>I', exact(client, 4))[0]
            assert name_size <= 4096
            exact(client, name_size)
            pixel_format = struct.pack('>BBBBHHHBBBxxx', 32, 24, 0, 1, 255, 255, 255, 16, 8, 0)
            client.sendall(b'\0\0\0\0' + pixel_format)
            client.sendall(struct.pack('>BBHi', 2, 0, 1, 0)) # raw only, stable size
            pixels = bytearray(width * height * 4)
            def frame():
                client.sendall(struct.pack('>BBHHHH', 3, 0, 0, 0, width, height))
                while True:
                    kind = exact(client, 1)[0]
                    if kind == 2:
                        continue
                    if kind == 3:
                        exact(client, 3)
                        size = struct.unpack('>I', exact(client, 4))[0]
                        assert size <= 65536
                        exact(client, size)
                        continue
                    assert kind == 0
                    exact(client, 1)
                    count = struct.unpack('>H', exact(client, 2))[0]
                    assert count <= 4096
                    for _ in range(count):
                        x, y, w, h, encoding = struct.unpack('>HHHHi', exact(client, 12))
                        assert encoding == 0 and x + w <= width and y + h <= height
                        for row in range(h):
                            start = ((y + row) * width + x) * 4
                            pixels[start:start + w * 4] = exact(client, w * 4)
                    return bytes(pixels)
            time.sleep(1)
            before = frame()
            assert len(set(before)) > 4, 'Guest framebuffer contains no test pattern'
            client.sendall(struct.pack('>BBHI', 4, 1, 0, ord('a')) + struct.pack('>BBHI', 4, 0, 0, ord('a')))
            wait_for('INPUT-KEY code=30 value=1', 20)
            after = before
            for _ in range(20):
                time.sleep(0.2)
                after = frame()
                if after != before:
                    break
            assert after != before, 'Guest did not redraw in response to input'
            client.sendall(struct.pack('>BBHH', 5, 1, width // 2, height // 2) + struct.pack('>BBHH', 5, 0, width // 2, height // 2))
            wait_for('INPUT-ABS', 20)
            for name, data in [('before.ppm', before), ('after-key.ppm', after)]:
                rgb = bytearray()
                for index in range(0, len(data), 4):
                    rgb += bytes((data[index + 2], data[index + 1], data[index]))
                (report / name).write_bytes(f'P6\n{width} {height}\n255\n'.encode() + rgb)
        with socket.socket(socket.AF_UNIX) as control:
            control.settimeout(5); control.connect(str(qmp_path))
            reader = control.makefile('rb')
            assert 'QMP' in json.loads(reader.readline())
            for command_name in ['qmp_capabilities', 'system_powerdown']:
                control.sendall((json.dumps({'execute': command_name, 'id': command_name}) + '\n').encode())
                while True:
                    reply = json.loads(reader.readline())
                    if reply.get('id') == command_name:
                        assert 'error' not in reply
                        break
        wait_for('QEMU-ANDROID-SHUTDOWN', 20)
        assert process.wait(timeout=20) == 0
        (report / 'result.json').write_text(json.dumps({'host_guest_pixels': True, 'host_guest_keyboard': True,
            'host_guest_pointer': True, 'guest_poweroff': True, 'android_renderer_verified': False,
            'physical_controller_verified': False}, indent=2) + '\n')
        print('Host guest framebuffer, keyboard/pointer events, redraw and poweroff verified; Android/physical input still pending')
    finally:
        if process.poll() is None:
            process.kill(); process.wait(timeout=10)
        vnc_path.unlink(missing_ok=True); qmp_path.unlink(missing_ok=True)
