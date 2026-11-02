# QEMU-Android development status

Updated: 2026-11-02. Current milestone: **A — initial Android ARM64 cross-build**.
Upstream base: `56afd82bd4db9688f1c1c9d0e2ea163b8cde3283` (11.1.50 development).
Official upstream: https://gitlab.com/qemu-project/qemu
Downstream: https://github.com/Hcode91/qemu-android

| Milestone | Source implemented | Compiled | CI tested | Boot verified | Real-device verified |
|---|---|---|---|---|---|
| A: Android ARM64 QEMU | Initial build scripts | No | Setup failed; fix submitted | No | No |
| B: Installable APK with QEMU | Initial frontend/packaging | No | Setup failed; fix submitted | No | No |
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

Latest observed status: **failed during SDK setup, before native compilation**.
Run: https://github.com/Hcode91/qemu-android/actions/runs/41457940560
Source commit: `b3e30185abe8ac7ee4d4cf2354858967d3dd4eb4`.
Primary error: `sdkmanager: command not found`. JDK and Gradle setup succeeded;
native compilation, Kotlin compilation, APK creation and their tests were skipped.
The independent OSV step also failed on native dependency findings.

Fix submitted: explicitly provision SDK command-line tools 16.0 using a pinned
setup action, upgrade zlib to 1.3.2, PCRE2 to 10.49 and GLib to 2.86.5 at verified
release commit SHAs, and adjust static-library CMake options for those versions.
Security failures remain blocking; advisories are now printed in job logs too.
The new commit triggers another run; its results have not been observed. The
owner asked the agent not to wait and will report completion.
Draft PR: https://github.com/Hcode91/qemu-android/pull/1

Latest successful APK: **none**.
Runs: https://github.com/Hcode91/qemu-android/actions
On a passing development run download `QEMU-Android-debug-<commit>`.
Evidence: `QEMU-Android-evidence-<commit>-<attempt>`.

## Known bugs and open risks

- NDK/Bionic compilation and executable packaging have not been tested. Meson
  feature probes, Linux-only APIs and static dependency linkage may require port fixes.
- Android 14 SELinux/executable-memory policy and split-W/X TCG need actual device
  testing. An extracted PIE is an architecture hypothesis, not a working-emulator claim.
- One running VM; activity recreation/unbinding terminates it. No background VM,
  image export/deletion, serial-log export or live profile editing yet.
- Networking disabled; no Android Internet permission. No graphics, touch,
  physical controller forwarding or audio. These controls are not simulated.
- Guest command line fixed for initramfs Linux. No guest binary is bundled and no
  Linux boot has been verified. CI boot harness is not wired to a fixture yet.
- The failed run reported zlib CVE-2026-22184, CVE-2026-27171, CVE-2026-3381
  and CVE-2026-76844; PCRE2 CVE-2026-103111, CVE-2026-89158 and
  CVE-2026-89161; and GLib CVE-2025-3360. Release pins have been upgraded in
  response. The replacement pins require a fresh OSV result; no clean scan is
  claimed. libffi/dtc had no findings in that report. Maven was not resolved.
  OSV commit coverage is incomplete.
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
