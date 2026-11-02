# QEMU-Android development status

Updated: 2026-11-02. Current milestone: **A — native compilation achieved; dependency closure pending**.
Upstream base: `56afd82bd4db9688f1c1c9d0e2ea163b8cde3283` (11.1.50 development).
Official upstream: https://gitlab.com/qemu-project/qemu
Downstream: https://github.com/Hcode91/qemu-android

| Milestone | Source implemented | Compiled | CI tested | Boot verified | Real-device verified |
|---|---|---|---|---|---|
| A: Android ARM64 QEMU | Native port/build scripts | Yes, compiled and linked | ELF dependency closure failed | No | No |
| B: Installable APK with QEMU | Initial frontend/packaging | No | Kotlin/APK steps not reached | No | No |
| C: Minimal ARM Linux | Test protocol/harness only | No guest fixture | No | No | No |
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

Latest observed status: **QEMU compiled and linked; ELF dependency validation failed**.
PR run: https://github.com/Hcode91/qemu-android/actions/runs/41492571109
Push run: https://github.com/Hcode91/qemu-android/actions/runs/41492559424
Source commit: `02413da368c933d561a9a0ef91f5b27d066bc2a8`.
All 1,992 Ninja targets completed, including linking qemu-system-arm for Android
ARM64. The ELF validator then found an unpackaged dependency: `libfdt.so`.
Architecture, Android linker, TEXTREL and RELRO/NOW checks before that assertion
passed. The later load-alignment check was not reached. Four fetch regressions
and the eight-commit native OSV scan pass. Maven, Kotlin and APK steps were not
reached. This is compilation evidence, not an installable APK or boot result.

Fix submitted: QEMU explicitly requests static libfdt when compiling for Android.
Dtc's static-build option is enabled and the archive is checked before QEMU
configuration. Other hosts retain the existing library selection. The ELF
system-library allowlist is unchanged: missing dependencies still fail CI.
The next build must recheck native closure/alignment and reach Kotlin/APK tests.
No APK or guest boot is claimed. The owner will report the next result.
Draft PR: https://github.com/Hcode91/qemu-android/pull/1

Latest successful APK: **none**.
Runs: https://github.com/Hcode91/qemu-android/actions
On a passing development run download `QEMU-Android-debug-<commit>`.
Evidence: `QEMU-Android-evidence-<commit>-<attempt>`.

## Known bugs and open risks

- QEMU has compiled and linked for ARM64, but ELF validation found an
  unpackaged libfdt dependency. Static linkage correction is submitted;
  complete native validation, Kotlin/APK compilation and runtime are pending.
- Android 14 SELinux/executable-memory policy and split-W/X TCG need actual device
  testing. An extracted PIE is an architecture hypothesis, not a working-emulator claim.
- One running VM; activity recreation/unbinding terminates it. No background VM,
  image export/deletion, serial-log export or live profile editing yet.
- Networking disabled; no Android Internet permission. No graphics, touch,
  physical controller forwarding or audio. These controls are not simulated.
- Guest command line fixed for initramfs Linux. No guest binary is bundled and no
  Linux boot has been verified. CI boot harness is not wired to a fixture yet.
- Latest native OSV step passed with no findings for the eight scanned commits.
  Maven dependencies remain unresolved, and downstream QEMU commit coverage is
  not guaranteed. No full application security pass is claimed.
- zlib and PCRE2 use pinned development snapshots because stable releases lacked
  recorded fixes. Their native builds and scan passed at the current pins;
  runtime verification remains pending. See the dependency fix notes.
- Gradle top-level versions are pinned; transitive checksum/lockfiles still need
  initial CI resolution. SDK/host package reproducibility is incomplete.
- Upstream's inherited Repo Lockdown workflow may close this first PR because
  pull_request_target reads master. The proposed downstream workflow removes
  that behaviour; it takes effect after owner-approved merge. No merge authorised.
- Signing secrets and protected environments have not been configured or inspected.

## Real-device requirements

Galaxy S23/Android 14: install CI debug APK, execute ARM64 instrumentation,
check actual native version, create VM, import a source-licensed virt-capable
ARMv7 zImage/initramfs/ext4 disk, record actual serial bytes and QMP state,
verify userspace, disk read/write across two boots and clean poweroff. Check
memory/JIT errors, process cleanup, rotation, import failures, disk locks, cold
start, 16 KiB compatibility and app data separation. Never disable Play Protect.
No performance measurements exist.

## Next priorities

1. Inspect the first Actions logs, fix native/Gradle/lint/security failures and
   repeat builds. Only mark A/B compiled after real passing evidence.
2. Build pinned, legally redistributable Linux/BusyBox fixtures in Actions,
   exercise the two-boot harness on host QEMU, then run on Android ARM64.
3. Add ARM64 device CI and retain instrumentation, guest serial and disk evidence.
4. Implement a software display transport (likely local QEMU VNC/RFB to Android
   Surface), input/controller forwarding, then audio. Benchmark before optimising.
5. Research AOSP 4.1 ARMv7 board/kernel adaptation. Original OUYA Tegra firmware
   cannot be assumed compatible with virt. No proprietary images, games, licensing
   bypasses, or performance/graphics claims without working evidence.
