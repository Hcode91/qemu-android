# QEMU-Android development status

Updated: 2026-11-02. Current milestone: **C — minimal ARM Linux guest boot (A/B build checks passed)**.
Upstream base: `56afd82bd4db9688f1c1c9d0e2ea163b8cde3283` (11.1.50 development).
Official upstream: https://gitlab.com/qemu-project/qemu
Downstream: https://github.com/Hcode91/qemu-android

| Milestone | Source implemented | Compiled | CI tested | Boot verified | Real-device verified |
|---|---|---|---|---|---|
| A: Android ARM64 QEMU | Native port/build scripts | Yes | Build and ELF validation passed | No | No |
| B: Installable APK with QEMU | Frontend/packaging | Yes, signed debug APK | Unit tests/lint/APK checks passed | No | Installed; VM creation reported |
| C: Minimal ARM Linux | Fixture build and two-boot harness | Yes, fixture built | Two host boots/persistence passed | First S23 boot shown | Kernel/userspace/disk write/power-down shown; second-boot persistence pending |
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

Latest observed status: **both development workflows passed**.
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

Milestone C now has passing host fixture tests and first-boot S23 evidence.
The remaining device check is persistence across a second boot, together with
QEMU exit/control behaviour. Copyable logs are being added to make that
verification practical. Milestone D graphics/controller work follows.

## Known bugs and open risks

- The baseline development build and static validation pass. The new copy-log
  feature needs its own CI build and clipboard test. Performance is unmeasured.
- The S23 first boot demonstrates extracted PIE execution and TCG for this
  fixture/device. Broader Android compatibility, JIT failure handling, lifecycle
  behaviour and performance still require testing.
- One running VM; activity recreation/unbinding terminates it. No background VM,
  image export/deletion, log file export or live profile editing yet.
- Networking disabled; no Android Internet permission. No graphics, touch,
  physical controller forwarding or audio. These controls are not simulated.
- Guest command line is fixed for initramfs Linux. Guest images are downloaded
  separately. First S23 boot is shown; second-boot persistence and scrolling-log
  behaviour still need captured evidence.
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

Second-boot PERSIST-READ has not been confirmed. The owner reports output that
keeps scrolling; this could be normal output or repeated boots, and the cause
is undiagnosed without captured logs. No reboot-loop diagnosis is claimed.
No performance benchmark has been performed.

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
Android permission or network access is added. Source is implemented; the new
APK, lint and clipboard behaviour are awaiting Actions/device verification.
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

1. Build and validate the copy-log update through Actions; capture the scrolling
   guest output and host diagnostics without assuming a reboot-loop cause.
2. Verify PERSIST-READ on a second S23 boot using the same imported disk and
   confirm QEMU process exit after guest shutdown.
3. Add ARM64 device CI and retain instrumentation, guest serial and disk evidence.
4. Implement a software display transport (likely local QEMU VNC/RFB to Android
   Surface), input/controller forwarding, then audio. Benchmark before optimising.
5. Research AOSP 4.1 ARMv7 board/kernel adaptation. Original OUYA Tegra firmware
   cannot be assumed compatible with virt. No proprietary images, games, licensing
   bypasses, or performance/graphics claims without working evidence.
