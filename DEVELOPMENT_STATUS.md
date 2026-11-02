# QEMU-Android development status

Updated: 2026-11-02. Current milestone: **D basic graphics/touch/controller-button goal verified on S23; E planning next**.
Upstream base: `56afd82bd4db9688f1c1c9d0e2ea163b8cde3283` (11.1.50 development).
Official upstream: https://gitlab.com/qemu-project/qemu
Downstream: https://github.com/Hcode91/qemu-android

| Milestone | Source implemented | Compiled | CI tested | Boot verified | Real-device verified |
|---|---|---|---|---|---|
| A: Android ARM64 QEMU | Native port/build scripts | Yes | Build and ELF validation passed | Linux fixture | Yes, via guest boot |
| B: Installable APK with QEMU | Frontend/packaging | Yes, signed debug APK | Unit tests/lint/APK checks passed | Linux fixture | Installed and guest boot shown |
| C: Minimal ARM Linux | Fixture build and two-boot harness | Yes | Two host boots/persistence passed | Yes, S23 | Kernel/userspace/disk persistence/unmount/power-down shown |
| D: Graphics/controller | Software RFB Surface, touch/keyboard/controller mappings, runtime maps and fixture | Yes, f9714c9 APK | Android gates and host framebuffer/input/redraw/shutdown passed | Graphical fixture boots on S23 | Visible guest pixels, touch and controller-button mappings verified; D-pad/sticks pending |
| E: Android ARM guest | Research pending | No | No | No | No |
| F: Android 4.1 test app | No | No | No | No | No |
| G: OUYA application | No | No | No | No | No |
| H: OUYA-style interface | No | No | No | No | No |

## Implemented source

- History-preserving downstream fork; Android additions isolated under `android/`.
- Separate development/release workflows; pinned core toolchains/actions/native
  dependency commits, Gradle/source caches, minimal job permissions and evidence artifacts.
- Genuine `qemu-system-arm` cross-build attempt, ARM64 NDK, TCG, libfdt, headless
  ARMv7 virt machine, raw virtio block devices. No KVM or external host sharing.
- Kotlin frontend with persistent profiles, RAM/CPU limits, document imports,
  blank raw disks, process ownership, separate guest serial/host diagnostics,
  private QMP controls and serial input. File locks protect images while running.
- ELF/APK/manifest validation, OSV queries, persistent-secret release signing,
  labelled unsigned fallback and explicit manual public-publishing opt-in.

## GitHub Actions and artifacts

Latest successful Android APK source: `f9714c941efec7a90e16453e529672e25e6aef72`.
Both Android runs passed native compilation, Kotlin/unit tests, instrumented-test
compilation, lint, native/Maven OSV and APK runtime-keymap/signature validation:
https://github.com/Hcode91/qemu-android/actions/runs/41542726206
https://github.com/Hcode91/qemu-android/actions/runs/41542734070
APK: https://github.com/Hcode91/qemu-android/actions/runs/41542726206/artifacts/13188115227
ARM64 instrumented tests compiled but have not been executed on a device runner.

Host graphical fixture source: `681d79d8968d9ceac705ec2157542a2adab5c70c`.
Both fixture runs passed persistence boots, actual guest framebuffer/input/redraw
and clean shutdown:
https://github.com/Hcode91/qemu-android/actions/runs/41529607295
https://github.com/Hcode91/qemu-android/actions/runs/41529616637
Images: https://github.com/Hcode91/qemu-android/actions/runs/41529607295/artifacts/13186585213

The owner now supplied an S23 screenshot with guest color bars and a serial log
showing virtio GPU/fb0, DISPLAY-READY at 1280x800/32bpp, and actual tablet/button
events. Graphics and touch forwarding are verified for this fixture/device.
See android/diagnostics/s23-graphics-touch-2026-11-02.md. The owner's follow-up
controller-test response now contains press/release events for x/y/Escape/q/
Enter/Tab/e, matching X/Y/B/L1/A-or-Start/Select/R1 mappings. See
android/diagnostics/s23-controller-buttons-2026-11-02.md. Basic Milestone D is
verified for this fixture/device. D-pad/stick events, focus behavior,
graphical-session shutdown/native exit and benchmarks remain unverified.
The new log also contains PERSIST-READ on the preceding graphical boot's disk.

The earlier S23 VNC en-us failure was addressed by packaging exact upstream maps
and supplying the private -L path. AndroidX instrumented-test compilation and
its Gradle enablement also passed in the current runs. The installed working
APK and existing Linux images can be retained for physical-controller testing.

Earlier host graphics failure at 6fa6199 was legacy Virtio MMIO; explicit modern
transport fixed it in 681d79d. Earlier Android archive validation failed at
6fa6199; safe Pixman link materialization fixed it at fd6537c. Those failures
are superseded by the passing CI run above.

Previous copy-log baseline: `4240f9a24e875326b80eb7828313fced3159dda8`.
APK: https://github.com/Hcode91/qemu-android/actions/runs/41514009590/artifacts/13173389273

Earlier baseline verified below:
PR run: https://github.com/Hcode91/qemu-android/actions/runs/41499762975
Push run: https://github.com/Hcode91/qemu-android/actions/runs/41499755001
Source commit: `012e026db650ff0246ef8612d9530535ae7eee12`.
PR merge checkout: `21c17cf462ad1a968b798c5a12f18787ede31170`.

Verified Actions steps: dependency-fetch regressions; Kotlin compilation, unit
tests and lint; QEMU ARM64 compilation/link; static fdt link probe; native ELF
closure/hardening/alignment; debug APK packaging; native/Maven vulnerability
scan; manifest, packaged-backend identity, ZIP/alignment and debug signature
validation; APK and evidence uploads. All reported success. No device install,
backend execution on Android or guest boot had been observed at that build checkpoint.
The later owner-supplied screenshot below provides first-boot device evidence.

Verified debug APK artifact used for the first device test:
https://github.com/Hcode91/qemu-android/actions/runs/41499762975/artifacts/13168483152
Name: `QEMU-Android-debug-21c17cf462ad1a968b798c5a12f18787ede31170`.
Evidence artifact:
https://github.com/Hcode91/qemu-android/actions/runs/41499762975/artifacts/13168553178
Artifacts expire 2026-11-16. The commit in the PR artifact name is GitHub's
merge checkout; the push run builds the branch commit directly.

Milestone C is verified for the Linux fixture on the owner's S23. The first
screenshot shows PERSIST-WRITTEN; the later copied serial log shows PERSIST-READ
on the same ext4 disk, followed by unmount and kernel power down. See
android/diagnostics/s23-linux-persistence-2026-11-02.md for the evidence excerpt
and its limits. Native process exit status and performance remain unmeasured.
Milestone D's basic software display/touch/controller-button goal is verified.
Follow-up validation covers D-pad/sticks, focus behavior and graphical-session
shutdown. Milestone E requires research/adaptation before an Android ARM guest
can boot; no Android/OUYA compatibility is yet claimed.

## Known bugs and open risks

- Development builds, unit tests, lint and APK validation pass for the copy-log
  update. The owner supplied a pasted guest log. No clipboard instrumentation
  suite or performance measurements have been run.
- The S23 first boot demonstrates extracted PIE execution and TCG for this
  fixture/device. Broader Android compatibility, JIT failure handling, lifecycle
  behaviour and performance still require testing.
- One running VM; activity recreation/unbinding terminates it. No background VM,
  image export/deletion, log file export or live profile editing yet.
- Networking disabled; no Android Internet permission. Software display and
  touch and controller-button forwarding work on the owner's S23; D-pad/sticks
  and focus handling remain unverified.
  Audio, analog virtual gamepad and Android/OUYA guest support remain absent.
- Guest command line is fixed for initramfs Linux. Guest images are downloaded
  separately. S23 persistence is shown. The latest complete capture reaches
  shutdown; no reboot loop is shown in it.
- Latest combined OSV step passed with no findings for 13 native/Maven entries.
  Dependency resolution now succeeds. Downstream QEMU commit coverage is not
  guaranteed, and no full application security pass is claimed.
- zlib and PCRE2 use pinned development snapshots because stable releases lacked
  recorded fixes. Their native builds and scan passed at the current pins;
  runtime verification remains pending. See the dependency fix notes.
- Gradle top-level versions are pinned; transitive checksum/lockfiles still need
  initial CI resolution. SDK/host package reproducibility is incomplete.
- Upstream's inherited Repo Lockdown workflow may close this first PR because
  pull_request_target reads master. The proposed downstream workflow removes
  that behaviour; it takes effect after owner-approved merge. No merge authorised.
- Signing secrets and protected environments have not been configured or inspected.

## Owner-reported device evidence

The owner reports the app opens and VM creation works on the Galaxy S23.
A later screenshot shows genuine Linux serial output: /init starts,
QEMU-ANDROID-USERSPACE appears, ext4 on vda mounts read/write, PERSIST-WRITTEN
appears, the filesystem unmounts, QEMU-ANDROID-SHUTDOWN appears and the kernel
reports reboot: Power down. This verifies guest kernel/userspace execution,
virtual disk writes and the guest's power-down path on that device. QEMU process
exit status is not shown in that screenshot.

The later owner-pasted serial log contains PERSIST-READ after mounting the same
ext4 filesystem UUID, then unmount, QEMU-ANDROID-SHUTDOWN and reboot: Power down.
This confirms data surviving between guest boots for the fixture. The capture
contains one full boot, with no kernel panic or repeated restart shown. It does
not establish native process exit status or explain every earlier scrolling
observation. No performance benchmark has been performed.
Evidence excerpt: android/diagnostics/s23-linux-persistence-2026-11-02.md.

## Milestone C fixture and diagnostic work

Linux fixture run 41503085561 succeeded:
https://github.com/Hcode91/qemu-android/actions/runs/41503085561
Images: https://github.com/Hcode91/qemu-android/actions/runs/41503085561/artifacts/13172783014
Buildroot images compiled and the two host-QEMU boot checks passed, including
kernel, userspace, persistence and poweroff. These host results are distinct
from the S23 screenshot evidence above. Full guest vulnerability clearance is
not claimed by hash validation or fixture tests.

New source change: a Copy logs section exposes Android clipboard snapshots of
the last 100 guest lines, the retained guest console, host diagnostics, or both
with explicit stream headings. It does not pause/reset the guest or synthesize
boot output. Existing stream retention remains 128 Ki characters each. No new
Android permission or network access is added. Both Android builds and lint
passed; the owner has supplied a copied log. Per-button instrumented clipboard
tests remain unperformed.
The owner asked the agent not to wait for CI completion.

## Real-device requirements

Galaxy S23/Android 14: install CI debug APK, execute ARM64 instrumentation,
check actual native version, create VM, import a source-licensed virt-capable
ARMv7 zImage/initramfs/ext4 disk, record actual serial bytes and QMP state,
verify userspace, disk read/write across two boots and clean poweroff. Check
memory/JIT errors, process cleanup, rotation, import failures, disk locks, cold
start, 16 KiB compatibility and app data separation. Never disable Play Protect.
No performance measurements exist.

## Milestone D source submission

- Statically pinned Pixman and QEMU VNC enabled in the genuine native build.
- App-private Unix RFB socket, bounded pure-Kotlin raw/resize client and software
  SurfaceView rendering. Persisted display opt-in defaults off for existing VMs.
- Touch tablet, physical keyboard and Android controller key/stick forwarding.
  Controllers map to keyboard keys, not full analog guest gamepads.
- Linux framebuffer/evdev test source, built-in virtio graphics/input drivers,
  and third host-QEMU boot checking actual pixels, input/redraw and shutdown.
- Actions-only protocol/archive safety tests; exact source and graphical evidence
  artifacts. New scripts have only received local syntax/static review.
- 681d79d passed full Android build/validation and the host graphical guest
  test, including real pixels, input-driven redraw and clean shutdown.
- The keymap/runtime correction passed CI. Owner-supplied color bars and
  DISPLAY-READY/INPUT-ABS/BTN_LEFT logs verify S23 pixels and touch forwarding.
- The owner's follow-up controller test shows expected mapped key down/up
  events. Basic Milestone D is verified; D-pad/sticks and focus remain unchecked.
- Audio, acceleration and performance evidence remain absent. Graphical-session
  shutdown/native exit also require a device capture.
- Pixman's archive is SHA-512 pinned, with bounded extraction and safe direct
  links materialized as regular files. Its OSV coordinate has incomplete coverage.
  fd6537c passed native/Maven scans; this does not establish a full security pass.
- The inherited master-branch Repo Lockdown action failed separately because its
  old token validator rejects the current token length. The development branch
  already scopes it away from this fork; default-branch workflow repair still
  requires an owner-approved merge. No merge has been performed.

See android/DISPLAY.md for architecture, limitations and S23 verification steps.

## Next priorities

1. Verify graphical-session guest shutdown plus QEMU exit status; keep the
   working APK and retain before/after pattern evidence.
2. Check D-pad/sticks separately (no arrow events in the supplied button log),
   focus release and simultaneous inputs. Basic button mappings are verified.
3. Check focus/input release, pause/resume, rotation cleanup and image isolation;
   add ARM64 device CI and retain instrumentation/display evidence.
4. Measure rendering and input latency before optimisation. Audio and proper
   virtual gamepad support remain later work.
5. Research AOSP 4.1 ARMv7 board/kernel adaptation. Original OUYA Tegra firmware
   cannot be assumed compatible with virt. No proprietary images, games or
   licensing bypasses are included.
