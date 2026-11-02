# QEMU-Android development status

Updated: 2026-11-02. Current milestone: **C — minimal ARM Linux guest boot (A/B build checks passed)**.
Upstream base: `56afd82bd4db9688f1c1c9d0e2ea163b8cde3283` (11.1.50 development).
Official upstream: https://gitlab.com/qemu-project/qemu
Downstream: https://github.com/Hcode91/qemu-android

| Milestone | Source implemented | Compiled | CI tested | Boot verified | Real-device verified |
|---|---|---|---|---|---|
| A: Android ARM64 QEMU | Native port/build scripts | Yes | Build and ELF validation passed | No | No |
| B: Installable APK with QEMU | Frontend/packaging | Yes, signed debug APK | Unit tests/lint/APK checks passed | No | Installation pending |
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
backend execution on Android or guest boot has been observed.

Latest successful debug APK artifact:
https://github.com/Hcode91/qemu-android/actions/runs/41499762975/artifacts/13168483152
Name: `QEMU-Android-debug-21c17cf462ad1a968b798c5a12f18787ede31170`.
Evidence artifact:
https://github.com/Hcode91/qemu-android/actions/runs/41499762975/artifacts/13168553178
Artifacts expire 2026-11-16. The commit in the PR artifact name is GitHub's
merge checkout; the push run builds the branch commit directly.

Next milestone: build a source-pinned, redistributable ARMv7 Linux kernel and
BusyBox initramfs through Actions; prepare an ext4 raw test disk; validate the
fixture with the existing two-boot headless harness; then verify actual kernel,
userspace, disk persistence and shutdown through the Android-packaged backend.
A host-side fixture boot alone does not verify Android execution. Milestone C
stays unverified until guest-boot evidence is captured, and real-device status
stays pending until the S23 has been tested.

## Known bugs and open risks

- Development build and static validation now pass. Real Android execution,
  installation, guest boot and performance remain unverified.
- Android 14 SELinux/executable-memory policy and split-W/X TCG need actual device
  testing. An extracted PIE is an architecture hypothesis, not a working-emulator claim.
- One running VM; activity recreation/unbinding terminates it. No background VM,
  image export/deletion, serial-log export or live profile editing yet.
- Networking disabled; no Android Internet permission. No graphics, touch,
  physical controller forwarding or audio. These controls are not simulated.
- Guest command line fixed for initramfs Linux. No guest binary is bundled and no
  Linux boot has been verified. CI boot harness is not wired to a fixture yet.
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
