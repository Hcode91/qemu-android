# First milestone D graphics failure — 2026-11-02

Observed Actions run: https://github.com/Hcode91/qemu-android/actions/runs/41518914618
Source: `6fa6199249c44a5a1bf4b282875e1dbd91d07d1d`.
Evidence artifact: 13180512457 (expires 2026-11-16).

The complete runner log could not be returned by the connector (Transport
closed). The retained build log, guest serial and kernel configuration were
read from the uploaded evidence ZIP using bounded HTTP range reads. No local
compilation or guest test execution was performed.

Observed: Buildroot/kernel/custom guest program compilation finished. Both
host serial boots verified userspace, ext4 persistence and poweroff. The third,
graphical boot entered the custom init but never produced DISPLAY-READY.

Excerpt from `display/guest-serial.log` (whitespace normalised):

```text
QEMU-ANDROID-USERSPACE
PERSIST-READ
QEMU-ANDROID-GRAPHICS
framebuffer open/info: No such file or directory
Kernel panic - not syncing: Attempted to kill init! exitcode=0x00000100
Rebooting in 1 seconds..
```

The same sequence repeated until the host harness timed out. Kernel configuration
showed `CONFIG_DRM_VIRTIO_GPU=y`, `CONFIG_DRM_VIRTIO_GPU_KMS=y`,
`CONFIG_DRM_FBDEV_EMULATION=y`, `CONFIG_DRM_CLIENT_DEFAULT_FBDEV=y`,
`CONFIG_FB_DEVICE=y`, `CONFIG_INPUT_EVDEV=y`, `CONFIG_VIRTIO_INPUT=y` and
`CONFIG_VIRTIO_MMIO=y`. Missing graphics drivers are not the cause shown here.

QEMU's `hw/virtio/virtio-mmio.c` defaults `force-legacy` to true in both Ubuntu's
QEMU 8.2 series and this fork's native source. Its legacy feature register does
not advertise VIRTIO_F_VERSION_1. Linux `virtio_gpu_init` in
`drivers/gpu/drm/virtio/virtgpu_kms.c` and `virtinput_probe` in
`drivers/virtio/virtio_input.c` reject devices lacking that feature with ENODEV.
This explains the absent framebuffer and Virtio input devices despite built-in
drivers. The serial disk uses a driver that supports legacy transport.

Correction: explicitly set `-global virtio-mmio.force-legacy=false` in graphical
app and host-test commands; retain the existing headless command. Add a VM-args
regression assertion, prevent host-test reboot loops, fail early on panic /
framebuffer errors, and retain `command.json`. New CI is required. Neither this
diagnosis nor the earlier successful APK is evidence of working guest graphics
or physical-controller delivery on Android.

Buildroot legal-info also reported missing custom-kernel licence hash entries
while retaining the corresponding sources. This is a separate provenance gap;
it was not the cause of the observed graphics-test timeout. Hash-validated guest
sources do not provide a full guest vulnerability clearance.
