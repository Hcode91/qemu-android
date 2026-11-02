# Owner-provided S23 graphics and touch evidence — 2026-11-02

The owner supplied a screenshot showing the guest's vertical color bars and
upper gradient in the Android guest display, plus copied guest serial output.
This follows the runtime-keymap fix and latest APK link. The screenshot alone
does not independently attest the installed APK's commit or signature.

Latest linked successful APK source: `f9714c941efec7a90e16453e529672e25e6aef72`.
APK run: https://github.com/Hcode91/qemu-android/actions/runs/41542726206
APK artifact: https://github.com/Hcode91/qemu-android/actions/runs/41542726206/artifacts/13188115227
Both push and PR Android runs passed. The Linux fixture passed at 681d79d.

Representative excerpt from the owner-pasted log (not a full capture):

```text
input: QEMU Virtio Tablet as /devices/platform/a003a00.virtio_mmio/virtio1/input/input0
input: QEMU Virtio Keyboard as /devices/platform/a003c00.virtio_mmio/virtio2/input/input1
[drm] Initialized virtio_gpu 0.1.0 for a003e00.virtio_mmio on minor 0
virtio-mmio a003e00.virtio_mmio: [drm] fb0: virtio_gpudrmfb frame buffer device
QEMU-ANDROID-USERSPACE
PERSIST-WRITTEN
QEMU-ANDROID-GRAPHICS
DISPLAY-READY width=1280 height=800 bpp=32 inputs=3
INPUT-KEY code=272 value=1
INPUT-ABS code=0 value=23269
INPUT-ABS code=1 value=30104
INPUT-KEY code=272 value=0
```

The capture then contains many additional absolute X/Y and button down/up events.
It contains no kernel panic, guest shutdown or native process exit status.

Verified for this fixture/device: guest kernel/userspace execution, virtio GPU
and framebuffer initialization, visible actual guest pixels on Android, guest
raw-disk write/unmount and forwarded tablet/button events. The guest generates
its own pattern through /dev/fb0; the Android UI renders QEMU's RFB pixels.

`code=272` is Linux BTN_LEFT. Combined with INPUT-ABS X/Y, it demonstrates the
pointer/touch path. It does not demonstrate a physical controller or keyboard.
The log's Virtio Keyboard registration only proves a virtual device exists.
There are no controller-mapped Enter/arrow/x/y key events in this capture.

Still pending: connected physical-controller key press/release evidence,
Android focus-release behavior, graphical-session shutdown and native exit
status, before/after frame evidence, lifecycle checks and performance measures.
Milestone D remains partially verified until physical input is demonstrated.
No Android guest, OUYA application, audio or accelerated graphics is implied.
