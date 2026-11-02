# QEMU-Android

Experimental downstream Android port in a history-preserving QEMU fork. Target:
Galaxy S23, ARM64 host, Android 14. All Android/native compilation, APK packaging,
signing and APK tests belong in GitHub Actions. No local APK build is required.

## First development build

Open the **Android development** Actions run for the PR/branch. On success download
`QEMU-Android-debug-<commit>` and unzip the APK. It uses a disposable CI debug
certificate, has a `.dev` package suffix and a Development application label.
CI certificates can change between runs; Android may require uninstalling an older
development APK, which deletes its private VM images. Export/back up valuable
images before uninstalling (an in-app export feature is still pending).

The workflow compiles the actual fork checkout for `arm-softmmu`, using pinned
NDK r28c, SDK 35, SDK command-line tools 16.0 (12266719), JDK 17, Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.20 and native
source pins. Native Git sources and declared submodules are fetched and commit-checked in Actions.
Pixman uses an official release archive verified by a pinned SHA-512.
Each declared submodule is checked against its parent gitlink and expected URL.
GLib's proxy-libintl fallback is explicitly fetched at the revision in its wrap
file; automatic Meson downloads stay disabled. Translations are disabled. QEMU's
own subprojects use its checked-in wrap/submodule revisions. Host apt package
versions follow the Ubuntu 24.04 runner; this is not a reproducible hermetic build.
Maven dependencies are versioned but transitive checksum verification and a
committed resolved lockfile still need the first CI resolution. No wrapper JAR
is downloaded/executed on the owner's machine.

## Current application

Create a profile with 128–3072 MiB RAM and 1–4 guest CPUs. Import a legally
obtained ARM Linux **zImage** (not an Android boot.img), optionally an initramfs,
and a **raw** virtual disk through Android's document picker. Images are copied
to app-private storage under UUID directories. Start runs `virt/cortex-a15`, TCG
and PL011 serial. A blank disk is sparse and unformatted; importing a formatted
ext4 disk is needed for a Linux persistence test. The kernel command line is
currently fixed to `console=ttyAMA0 rdinit=/init panic=1`; this is an initramfs
boot path, not a generic Android guest boot path.

Pause/resume/reset/shutdown use the real QMP server over an app-private Unix
socket. Guest shutdown requires a guest handling the virtual power button; Stop
QEMU or Force stop can corrupt a mounted guest filesystem. The guest console
shows QEMU stdout, while QMP responses and host errors use a separate diagnostics
pane. Logs are bounded to the latest 128 Ki characters per stream in memory. The
**Copy logs** section above the console copies the last 100 guest lines, the
retained guest console, host diagnostics, or both streams with clear headings
to Android's clipboard. Copying reads a snapshot without pausing or restarting
the guest. Clipboard contents can then be pasted into a diagnostic report.
File export is not implemented. A bound service owns one child process. Closing/recreating the UI
stops the VM; background execution and rotation continuity are not implemented.

The native binary is a PIE executable packaged as `libqemu-system-arm.so`, with
legacy native-library extraction enabled, then executed from the package
installer's `nativeLibraryDir`. It is **not** a JNI library and is never dlopened.
This avoids trying to execute writable imported files on Android 10+. The owner
has verified Linux execution and persistence on an S23 running Android 14;
broader SELinux/JIT and lifecycle compatibility still need testing. Split W/X TCG is
requested; there is no root, KVM or Termux. The optional software Surface renderer
is described in [DISPLAY.md](DISPLAY.md); GPU acceleration is not implemented.
JNI embedding remains a fallback if real-device policy prevents this architecture.

Bionic has no POSIX named shared-memory API or separate librt. The downstream
Meson configuration skips librt on Android, and `qemu_shm_alloc` uses QEMU's
existing memfd helper for anonymous fd-owned shared memory. Kernel allocation,
resize and SELinux errors remain visible; no permission or JIT policy is weakened.
QEMU has compiled, linked and passed native validation in Actions. The earlier
Linux fixture booted on the S23; new graphics code remains unverified. Android selects the static libfdt archive through pkg-config metadata. An early
NDK link probe and the final ELF validator reject unpackaged shared dependencies.
Direct-I/O statx alignment querying is enabled only after a compile/link feature
check; API levels without it use QEMU's existing alignment probing fallbacks.

## Verification limits

See [DEVELOPMENT_STATUS.md](../DEVELOPMENT_STATUS.md). Kotlin compilation, unit tests and lint run early in
Actions before native compilation. Packaging still requires the genuine backend. The ARM64 instrumentation test executes the packaged backend's version
command; it requires an ARM64 Android runner and is **not yet wired to a device**.
An x86 hosted emulator cannot execute this ARM64-only backend through native
hardware acceleration. No device test is implied by an APK artifact.

The [Linux fixture](guest/README.md) workflow builds source-pinned Linux/BusyBox
images and verifies kernel output, PID 1 userspace, disk persistence across two
host boots and poweroff. The owner also verified the earlier fixture on S23.
The new graphical fixture adds actual framebuffer and keyboard/pointer checks
on host QEMU. These do not verify the Android Surface or a physical controller.
No proprietary firmware, games or OUYA artwork are included.

Enable **Software guest display and input** for the new graphical Linux fixture.
The [display instructions](DISPLAY.md) explain the private RFB transport, initial
controller-to-keyboard mappings, required images and device verification steps.

## Security and licensing

No Android permissions, Internet access, telemetry, host directory exports,
external QMP listeners or arbitrary emulator command lines in phase one. Imports
are bounded, copied without trusting provider filenames, committed by rename,
and protected against concurrent VM writes with a file lock. QCOW magic is
rejected and block format is fixed to raw, preventing backing-file traversal.
Private app storage is not a security boundary against a compromised native
process running under the app's UID. Do not run untrusted images or put host
secrets into this experimental app. TCG does not provide the upstream QEMU
hardware-virtualization security guarantee.

OSV checks native commits, Pixman's OSS-Fuzz package/version coordinate and
resolved Maven runtime/test coordinates, failing
on findings or lookup failure. Commit coverage in OSV is incomplete; a clean
report is not proof of safety. Pinned upstream snapshots address the latest reported findings; see
`ci/SECURITY_FIXES.md`. They still require the next CI security and build checks. Build-tool/SDK supply-chain scanning is
not comprehensive yet. Native ELF inspection enforces AArch64 PIE, Android linker,
16 KiB load alignment, no text relocations, RELRO/NOW and a system-only dependency
closure. APK validation checks permissions, merged manifest, variant, ZIP,
packaged backend identity, signature and ZIP alignment. Reports and exact native
dependency sources are artifacts even when later checks fail.

Android additions are GPL-2.0-or-later. QEMU's original copyright notices,
COPYING/COPYING.LIB and individual file licences remain intact. Dependency
licences are recorded in `ci/native-deps.json`; full dependency source archives
are in CI evidence. Before distributing APKs outside development, accompany them
with exact QEMU and dependency sources and build instructions, including LGPL
static relinking requirements. Release publishing is currently gated separately;
see [SIGNING.md](SIGNING.md).

This downstream work uses AI assistance. Do not submit these generated changes
upstream: QEMU's `docs/devel/code-provenance.rst` disallows AI-generated upstream
contributions. Nothing in this fork claims an upstream DCO certification.
