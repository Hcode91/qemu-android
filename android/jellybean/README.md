# Genuine Jelly Bean native userspace bring-up

This is the next milestone E stage after the kernel ABI probe passed. Source
has compiled successfully in Actions. Early init/service execution is observed,
but the complete boot validation has not yet passed CI.
There is no Android framework, launcher, full system image or phone guest here.
Keep the existing f9714c9 APK and graphics-capable Linux fixture installed.

## Build and boot path

`project-lock.json` resolves all 293 projects in the AOSP android-4.1.2_r1
manifest to full commit IDs. The pinned manifest itself is
7af362b848fba5f21ada2c2b9c39d396c9651190; its bytes have a recorded SHA-256.
These pins were obtained by reading primary Gitiles metadata, without compiling
or executing source. The native bring-up workflow checks out a defined subset
of those projects for BUILD_TINY_ANDROID. It verifies the investigated build,
system/core, Binder and gralloc pins against the previous source audit. No
moving branch/tag is used for Git source checkout.

At 44238be both Android APK builds and the genuine AOSP native compilation
passed. The kernel ABI probe passed again. In both host-QEMU native boots, real
AOSP init started the Bionic service as its child; property reads, Binder 7,
shared ashmem, liblog and poweroff succeeded, and QEMU exited 0. The full native
boot test still failed: three mandatory init-action markers were missing.
The downloaded result.json confirms these were the only missing assertions.

The fixture wrote to /dev/kmsg before starting ueventd. AOSP init first mounts
a fresh tmpfs on /dev; its write builtin uses O_CREAT. The first marker therefore
created a regular file before ueventd could create the kernel character device.
The correction starts ueventd, waits for /dev/kmsg, and then writes the markers
with explicit informational priority. The native service additionally requires
/dev/kmsg to be a character device. All original marker assertions remain; no
success output is supplied by the host. The harness prints its JSON result and
records failures/timeouts as evidence. Correction CI is pending.
See ../diagnostics/aosp-native-boot-2026-11-03.md for run links and observed output.

The verified source closure includes frameworks/native product configuration,
historical NDK r5/API 9/SDK inputs, and both i686 and x86_64 host GCC toolchains
at release-locked commits. Static input checks and execution of both host
compiler modes precede the main build. Make collects independent failures while
retaining a failing status, and Actions saves verified sources before compilation.

The builder uses the digest-pinned official Ubuntu 12.04 image, archived signed
Ubuntu packages, Python 2, GNU make 3.81 and OpenJDK 6. The sole upstream-build
patch removes the old OpenJDK vendor rejection; actual Java/javac 1.6 checks are
retained. The patch is saved in build evidence. This only attempts C native
targets and does not claim full-platform Java compatibility. Package versions
and the resulting Docker image ID are recorded; archived package dependencies
are not yet fully version/checksum locked independently of their signed archive.
No unauthenticated APT mode or signature-check bypass is enabled.

The container runs the build with networking disabled. It compiles **real AOSP
init, Bionic, libcutils, liblog and libfs_mgr**, plus the small native-probe
service added under system/core. The resulting init and service must be static
ELF32 ARM executables. Their exact hashes and the initramfs hash are retained.
The rootfs contains AOSP init as PID 1 and a genuine ueventd symlink to it.
The custom init.rc starts ueventd, sets a property during boot actions and starts
the probe service through an Android property trigger.

Host QEMU uses the previously tested Linux 3.18/vexpress-a9 kernel. Test success
requires actual serial/kmsg output from init actions and its Bionic-linked child,
correct parent PID, a property-area read, Binder protocol 7, two ashmem mappings
sharing data, a liblog write and Android's poweroff path. Missing markers, panic,
failed native checks or nonzero QEMU exit fail CI. A compiler success alone is
not the success criterion.

The native-probe test instrumentation prints explicit markers. These are produced
inside the guest by the real init interpreter and compiled native service, never
by the Android app. No guest message is synthesized by the host harness.

## Evidence and security scope

The workflow uploads source locks, exact build compatibility patch, native
corresponding-source archives, builder package list/image ID, binary hashes,
kernel source/config/compiler evidence, real guest serial, command and result.
OSV commit queries cover the selected native guest/kernel/toolchain sources;
lookup failures fail the job. Findings are retained as research evidence and
reported, not described as a security clearance. The legacy guest and builder
are obsolete and may contain known vulnerabilities. There is no release image
or phone-installable bundle from this research job. Existing APK security and
release signing gates are independent and remain required.

The current closure builds native boot components. It does not build Dalvik,
zygote, system_server, SurfaceFlinger, app installation or the Android dashboard.
A successful run is **early AOSP native userspace verification**, not completion
of milestone E. Framework/system images, partition mounts and guest display/input
adaptation remain next. OUYA and Android 4.1 test applications are later work.

Primary sources:

- https://android.googlesource.com/platform/manifest/+/7af362b848fba5f21ada2c2b9c39d396c9651190/default.xml
- https://android.googlesource.com/platform/build/+/25454bc3d2925d6b930d9c515f5c090785b11135/core/main.mk
- https://android.googlesource.com/platform/system/core/+/ca38f2c7cde05b67bc8d582040210c4aad303298/init/Android.mk
- https://source.android.com/docs/setup/start/older-versions
