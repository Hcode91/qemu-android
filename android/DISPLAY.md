# Software guest display: milestone D

Status: host graphics/input/shutdown checks passed at `681d79d`; the runtime-map
APK correction passed Android CI at `f9714c9`. The owner's S23 screenshot now
shows real guest color bars, and serial output confirms framebuffer readiness
and tablet/button delivery. Graphics and touch are device verified. Physical
controller input and graphical-session shutdown/native exit remain pending.
See diagnostics/s23-graphics-touch-2026-11-02.md for the evidence limits.

## Data path

Linux virtio-gpu framebuffer -> genuine QEMU software display -> QEMU VNC/RFB
server on app-private `filesDir/vnc.sock` -> bounded Kotlin RFB 3.8 raw-pixel
client -> Bitmap on an Android SurfaceView canvas. No TCP listener, Android
Internet permission, root access, external VNC application or GPU acceleration
is required. Pixman is compiled statically into the native backend. QEMU's upstream keyboard maps are copied into the
APK as assets by Actions, checked byte-for-byte during APK validation, installed
atomically in app-private storage and located with `-L` before VNC starts. There is no
OpenGL ES/Vulkan guest acceleration, audio pipeline or latency benchmark yet.

The client accepts displays up to 2048 by 2048 pixels, bounded rectangles and
server names/clipboard messages. It requests 32-bit little-endian true-color
pixels, Raw rectangles and DesktopSize updates. Frame snapshots are immutable
once handed to the UI. Guest clipboard messages are discarded. A shared writer
lock prevents keyboard/pointer packets and framebuffer requests interleaving.
The UI checks for a new snapshot every 100 ms; this is a chosen polling interval,
not measured display performance. Aspect-fit scaling uses black letterboxing.

The display option is saved per VM and defaults off for existing profiles.
Headless serial/persistence boots remain available. Graphical mode adds genuine
`virtio-gpu-device`, `virtio-keyboard-device` and `virtio-tablet-device` hardware.
It explicitly sets `-global virtio-mmio.force-legacy=false`: Linux's GPU and
input drivers require `VIRTIO_F_VERSION_1`, while the MMIO transport defaults to
legacy in both the host test QEMU and our native QEMU source. This setting is
shared by the app command and host graphical test. Headless mode keeps its
previous transport selection.
The fixed Linux command line enables the fixture's graphical test mode. This
is still a Linux initramfs test path, not an Android guest boot implementation.

## Input

Tap the guest display or press **Focus guest display** to route input there.
One-finger touch becomes an absolute tablet position and primary mouse button.
It is not guest Android multi-touch. Physical keyboards send X11 keysyms over
RFB; printable characters, arrows, Enter, Escape, Backspace, Tab and modifiers
are supported. There is no IME text editor or complete international layout yet.

Already connected Android HID controllers use these initial keyboard mappings:

| Controller control | Guest key |
|---|---|
| D-pad / left stick | Arrow keys (stick dead zone 0.5) |
| A / Start | Enter |
| B | Escape |
| X / Y | x / y |
| L1 / R1 | q / e |
| Select | Tab |

Keys are released when display focus or its Surface is lost. D-pad key events
and joystick hat events share held-key state. This is keyboard emulation, not a
virtual analog gamepad or OUYA API implementation. Physical controller delivery
through Android still needs S23 testing. Bluetooth pairing uses Android's own
settings; the app does not request Bluetooth permissions.

## Actions evidence

The Android development workflow compiles and tests the bounded RFB client,
builds actual ARM64 QEMU with Pixman/VNC, and runs the existing lint, security,
ELF, manifest and signed APK validation. JVM protocol tests check raw colors,
partial-update snapshot ownership, resize/input packet encoding, oversized /
out-of-bounds data and unsupported encodings. They do not verify Android Canvas.

The ARM Linux fixture workflow builds framebuffer and evdev drivers into its
kernel and compiles `guest/display-test.c` with the pinned Buildroot toolchain.
Two serial persistence boots run first. A third host-QEMU boot writes actual
Linux `/dev/fb0` color bars, forwards an RFB 'a' key to `/dev/input/event*`, checks
that the guest redraws, checks tablet events, requests QMP power-down and requires
clean guest shutdown/process exit. Reports include genuine before/after PPM
frames, serial logs and `display/result.json`. This validates host QEMU and the
fixture; it does not execute the Android-packaged ARM64 binary or SurfaceView.

Pixman 0.46.4 uses the official Xorg release archive with the SHA-512 recorded in
Buildroot's `package/pixman/pixman.hash` at Buildroot commit
`7fdeadb3b9f12f78e5fccca914d7fef47090ab9a`. Download and extraction sizes are
bounded. Direct relative symlinks to regular archive members are materialized as
regular files; escaping/missing targets, link chains, hardlinks, special files,
duplicate paths and traversing paths are rejected. OSV
checks its explicit OSS-Fuzz package/version coordinate. Coverage for version
queries and downstream QEMU commits is incomplete; an empty result is not a
vulnerability clearance. Exact dependency sources remain CI artifacts.

## S23 verification after both workflows pass

1. Download the new development APK from **Android development** and the new
   images from **ARM Linux guest fixture**, both for this change. Older guest
   images lack the display fixture and may simply boot and shut down headlessly.
2. Use a separate test VM (512 MiB, 2 CPUs). Import `kernel.zImage`,
   `initramfs.cpio.gz` and the pristine `disk.raw`. Do not overwrite a valuable
   disk or reimport the old VM's disk just to test graphics.
3. Enable **Software guest display and input**, then Start. The guest should
   report `QEMU-ANDROID-GRAPHICS` and `DISPLAY-READY`. Scroll to the guest display
   and look for color bars. This fixture intentionally stays running for input.
4. Tap/drag the display. Guest serial should show `INPUT-ABS` and button events.
   Focus the display and use a connected controller's D-pad, stick and A/B/X/Y.
   Retain actual `INPUT-KEY` lines and a screenshot of the changed guest pattern.
5. Press **Guest shutdown**. Require `QEMU-ANDROID-SHUTDOWN`, kernel power-down
   and host `QEMU exit status: 0`. Test Pause/Resume and input release on focus
   loss. Existing activity rotation still stops the VM; continuity is pending.

If the APK cannot update because a prior CI certificate differs, retain the
exact Android installation error before deciding how to proceed. Uninstalling
removes app-private VM images; in-app export remains pending. Never disable
Play Protect. If graphics fails, copy guest and host logs separately. Do not
mark D complete until actual Android guest pixels and a physical controller's
input have both been demonstrated.
