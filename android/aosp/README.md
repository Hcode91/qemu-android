# Milestone E: Jelly Bean ARM guest prerequisites

Goal: boot genuine Android ARM userspace, then Android 4.1 applications. Current
state: source investigation and a kernel compatibility probe implemented;
no Android userspace has been compiled or booted. Keep the working Milestone D
APK and Linux fixture for existing phone tests.

## First candidate

AOSP `android-4.1.2_r1` generic ARMv7 userspace is the initial target. The selected
manifest, build, init, Binder and default-gralloc sources are pinned by full Git
commit in sources.json. This is not yet a fully resolved lock of every AOSP
project. The workflow fetches the selected source files, verifies investigated
requirements and retains their exact bytes, URLs and SHA-256 hashes.

Stock generic init mounts MTD/YAFFS system/data/cache and imports a hardware rc.
Our existing guest uses a raw virtio disk. System partition mounts and a hardware
init/ueventd configuration must therefore be adapted before running Android.
The generic board also enables emulator OpenGL; that configuration must be
replaced with working software framebuffer/EGL behavior. The default gralloc
source supports both `/dev/graphics/fbN` and `/dev/fbN`, but that alone does not
establish SurfaceFlinger or Android display compatibility.

The first kernel candidate is Linux 3.18.140 on QEMU `vexpress-a9`/Cortex-A9.
Its staging Android Kconfig explicitly supports the old 32-bit Binder ABI,
ashmem and Android kernel logging. Its PL111 framebuffer avoids depending on
Virtio GPU, which is not present in this kernel version. This choice trades
virtual hardware changes for fewer legacy Android kernel ABI backports. It is
an experimental obsolete guest kernel, with no vulnerability clearance; it is
not the host kernel or a proposed production security baseline. No guest network
interface or host filesystem sharing is configured.

The alternative is to retain `virt` and port legacy Android ABIs into a newer
kernel. That needs investigation rather than assuming Linux 6.18 can boot JB.
The kernel candidate is not yet selected as the final phone guest platform.

## Actions probe

`Android guest compatibility research` performs two concrete checks:

1. Audit the pinned primary AOSP files for ARMv7, separate kernel provisioning,
   Binder version negotiation, MTD mounts, hardware rc import and fbdev support.
2. Cross-build the pinned kernel with pinned AOSP GCC 4.6, then boot it in host
   QEMU with a tiny freestanding ARM initramfs program. The program uses real
   Linux syscalls to require Binder protocol 7, ashmem size ioctls, logger ABI 1
   and an actual write, a framebuffer device, then guest poweroff. Failures and
   kernel panics fail the workflow. Required Kconfig options are checked before
   compiling, and exact compiler/config/source/serial evidence is retained.

This probe is independently implemented C, **not Android init, Bionic, Dalvik or
an Android system image**. Passing it only confirms selected kernel interfaces.
The test does not claim framework startup, Android graphics, Android app launch,
OUYA compatibility or performance. Native application security gates stay in the
existing APK workflows. Guest/kernel/legacy-toolchain vulnerability coverage is
not comprehensive; exact source pins and source archives are provenance, not a
security clearance. No proprietary image, firmware, game or Google apps are used.

The AOSP compiler is executed only in Actions. Local work is source inspection
and syntax review. The new workflow does not replace the two Android APK
build/signing workflows and does not publish releases. Its artifacts are
research evidence, not a new phone-installable guest bundle.

## Work after prerequisite CI

- Resolve all required AOSP projects to immutable commits and establish a
  separate legacy userspace builder. Official documentation calls for Ubuntu
  12.04, JDK 6 and make 3.82 for this generation; a contemporary APK JDK/NDK
  installation is not the Jelly Bean guest build environment.
- Build genuine AOSP init/Bionic and system userspace, retain project licences
  and corresponding sources, and verify actual init output first.
- Adapt system/data/cache mounts and guest device permissions. Exercise Binder,
  ashmem, Android logging, Dalvik/zygote and the property/service manager.
- Implement the chosen virtual board's boot profile in the app, including safe
  DTB import if using vexpress. Do not offer a claimed working Android preset
  before the images and machine have passed a real boot.
- Bring up SurfaceFlinger with software rendering, input and persistent storage;
  require framework/service output and actual Android UI evidence for E.

Primary sources:

- https://android.googlesource.com/platform/manifest/+/7af362b848fba5f21ada2c2b9c39d396c9651190/default.xml
- https://android.googlesource.com/platform/build/+/25454bc3d2925d6b930d9c515f5c090785b11135/target/board/generic/BoardConfig.mk
- https://android.googlesource.com/platform/system/core/+/ca38f2c7cde05b67bc8d582040210c4aad303298/rootdir/init.rc
- https://github.com/gregkh/linux/blob/6b1ae527b1fdee86e81da0cb26ced75731c6c0fa/drivers/staging/android/Kconfig
- https://www.qemu.org/docs/master/system/arm/vexpress.html
- https://source.android.com/docs/setup/start/older-versions
