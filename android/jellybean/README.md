# Genuine Jelly Bean native userspace bring-up

The native stage of milestone E passed both host-QEMU boot checks and Android
APK checks at 2651036. Real AOSP init/Bionic/service execution, init markers,
Android properties, Binder, ashmem, liblog and poweroff are CI verified.
There is no Android framework, launcher or full Android system image yet.

The next source stage adds a separate phone VM profile and packages the exact
host-tested guest with checksums and corresponding sources. Its CI and S23 boot
remain pending. Keep the working Linux VM; do not replace its guest files.
Phone instructions: [DEVICE_TEST.md](DEVICE_TEST.md).

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
records failures/timeouts as evidence. Correction 2651036 passed both runs:
https://github.com/Hcode91/qemu-android/actions/runs/41654622649
https://github.com/Hcode91/qemu-android/actions/runs/41654632047
Both returned aosp_native_userspace_passed=true, missing=[], QEMU exit 0 and
no timeout. Android framework/UI/phone verification remain false.
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
from this research job. The next packaging step exposes only this early-native
research guest for a separate phone VM, and only after the full host boot test
passes. It verifies the exact boot input paths/initramfs hash, copies images
without modification, records SHA-256 hashes and includes kernel/native
corresponding sources. The app profile fixes vexpress-a9/Cortex-A9, 512 MiB,
1 CPU, serial only, no disk or networking; it requires an imported DTB. Existing
Linux profiles default to virt and retain their files. Basic bounded FDT header
checks reject malformed imports; QEMU still parses the full guest device tree.
Native build evidence requires both board configurations. Profile/import unit
tests, new bundle packaging and the aligned split-WX host boot run in Actions;
the new source has received static review only so far. Existing APK security
and release signing gates remain independent and required.

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

## Device-profile CI follow-up, 2026-11-04

Both APK runs passed at ead2df6. Native compilation and the kernel probe also
passed, but both aligned host boots failed before guest execution because
-nodefaults removed implicit audio setup. Vexpress still creates its PL041
sound device. No guest bundle was uploaded. The correction binds the machine's
audiodev property to an explicit none backend in both app and host harness;
it adds no playback functionality. The profile unit test requires that binding.
Correction CI and phone execution remain pending. Keep the installed APK.
