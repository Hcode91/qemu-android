# Genuine Jelly Bean native userspace bring-up

This is the next milestone E stage after the kernel ABI probe passed. Source
is implemented; this new source/build/boot workflow has not yet passed CI.
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

Initial CI fetched the pinned sources, completed the dependency query, built the
legacy container and ran Java 6/make 3.81. AOSP product parsing then failed because
frameworks/native was absent from the native checkout: vbox_x86 imports one of its
heap-configuration files even when compiling native ARM targets. The checkout
now includes that pinned project. A static preflight follows literal mandatory
inherit-product references and reports missing files before container compilation;
optional/dynamic expressions still remain checks for the actual Make build.
Correction CI is pending; no native userspace compile or boot has passed yet.

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
