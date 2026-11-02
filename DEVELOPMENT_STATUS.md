# QEMU-Android development status

Updated: 2026-11-02. Current milestone: **C verified for the Linux fixture on S23; D graphics/controller work next**.
Upstream base: `56afd82bd4db9688f1c1c9d0e2ea163b8cde3283` (11.1.50 development).
Official upstream: https://gitlab.com/qemu-project/qemu
Downstream: https://github.com/Hcode91/qemu-android

| Milestone | Source implemented | Compiled | CI tested | Boot verified | Real-device verified |
|---|---|---|---|---|---|
| A: Android ARM64 QEMU | Native port/build scripts | Yes | Build and ELF validation passed | Linux fixture | Yes, via guest boot |
| B: Installable APK with QEMU | Frontend/packaging | Yes, signed debug APK | Unit tests/lint/APK checks passed | Linux fixture | Installed and guest boot shown |
| C: Minimal ARM Linux | Fixture build and two-boot harness | Yes | Two host boots/persistence passed | Yes, S23 | Kernel/userspace/disk persistence/unmount/power-down shown |
| D: Graphics/controller | No | No | No | No | No |
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

Latest observed status: **copy-log update passed both Android runs**.
Latest source: `4240f9a24e875326b80eb7828313fced3159dda8`.
PR run: https://github.com/Hcode91/qemu-android/actions/runs/41514018172
Push run: https://github.com/Hcode91/qemu-android/actions/runs/41514009590
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
Next milestone: D, software graphical output and physical controller input.

## Known bugs and open risks

- Development builds, unit tests, lint and APK validation pass for the copy-log
  update. The owner supplied a pasted guest log. No clipboard instrumentation
  suite or performance measurements have been run.
- The S23 first boot demonstrates extracted PIE execution and TCG for this
  fixture/device. Broader Android compatibility, JIT failure handling, lifecycle
  behaviour and performance still require testing.
- One running VM; activity recreation/unbinding terminates it. No background VM,
  image export/deletion, log file export or live profile editing yet.
- Networking disabled; no Android Internet permission. No graphics, touch,
  physical controller forwarding or audio. These controls are not simulated.
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

## Next priorities

1. Begin milestone D with software guest display output to Android, using
   actual guest framebuffers and a testable transport. No GPU acceleration claim.
2. Add physical-controller/input forwarding and retain visual/input evidence.
   Keep native process exit/lifecycle verification as a separate pending check.
3. Add ARM64 device CI and retain instrumentation, guest serial and disk evidence.
4. Implement a software display transport (likely local QEMU VNC/RFB to Android
   Surface), input/controller forwarding, then audio. Benchmark before optimising.
5. Research AOSP 4.1 ARMv7 board/kernel adaptation. Original OUYA Tegra firmware
   cannot be assumed compatible with virt. No proprietary images, games, licensing
   bypasses, or performance/graphics claims without working evidence.
