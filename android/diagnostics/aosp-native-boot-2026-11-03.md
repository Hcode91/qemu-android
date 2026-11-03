# AOSP native userspace CI evidence, 2026-11-03

Source: `44238be5fdaf97a63e7469e5da0d18c94f12e165` (PR #5).
This is host-QEMU early native userspace, not Android framework or phone boot.

Both Android APK workflows passed:

- https://github.com/Hcode91/qemu-android/actions/runs/41650775317
- https://github.com/Hcode91/qemu-android/actions/runs/41650786020

Both native jobs compiled genuine AOSP init/Bionic/service and passed the
separate freestanding kernel ABI probe, then failed the full native boot gate:

- https://github.com/Hcode91/qemu-android/actions/runs/41650775222
- https://github.com/Hcode91/qemu-android/actions/runs/41650786147

Original evidence artifact:
https://github.com/Hcode91/qemu-android/actions/runs/41650775222/artifacts/13237602047

The artifact's `aosp-userspace/boot/result.json` says:

```json
{
  "aosp_native_userspace_passed": false,
  "missing": [
    "QEMU-AOSP-INIT-EARLY",
    "QEMU-AOSP-INIT-ACTIONS",
    "QEMU-AOSP-PROPERTY-TRIGGER"
  ],
  "qemu_exit_status": 0,
  "android_framework_booted": false,
  "android_ui_verified": false,
  "phone_verified": false
}
```

Excerpt from the same artifact's actual guest serial (NUL characters omitted):

```text
init: processing action 0x27388 (property:qemu.android.native_boot=ready)
init: command 'write' r=0
init: starting 'native_probe'
init: command 'start' r=0
AOSP-BIONIC-SERVICE pid=985 ppid=1
AOSP-PROPERTY-READ-OK
AOSP-BIONIC-BINDER-OK
AOSP-BIONIC-ASHMEM-OK
AOSP-LIBLOG-WRITE-OK
AOSP-NATIVE-USERSPACE-PASS
SysRq : Emergency Remount R/O
Emergency Remount complete
reboot: Power down
```

This establishes real early AOSP init/service execution with those native
checks, while the required init-action serial assertions remain failed.
The service PASS marker is not the full workflow's verdict.

## Cause and correction

Pinned AOSP system/core commit
`ca38f2c7cde05b67bc8d582040210c4aad303298` establishes the ordering:

- `init/init.c` mounts a fresh tmpfs over `/dev` before reading init.rc.
- `libcutils/klog.c` creates a temporary `/dev/__kmsg__` device and unlinks
  its name after opening it; this does not create `/dev/kmsg`.
- `init/builtins.c`'s write_file opens its destination with O_WRONLY|O_CREAT.
- Our early-init action wrote `/dev/kmsg` before starting ueventd. It therefore
  created a regular file. Later writes also succeeded into that file, and
  ueventd could not replace it with its character device.

The correction starts ueventd first, waits for `/dev/kmsg`, and then writes the
same three mandatory markers with explicit kernel-log priority. The native
probe additionally checks S_ISCHR for that path. No original assertion is
removed; a failed device wait still fails marker/device verification. CI must
validate the corrected complete boot. No local compilation or tests were run.

Missing optional initlogo and persistent-property directory notices, and host
ALSA device warnings, also appear in the log. They did not prevent this native
service execution and do not explain the three missing markers.
