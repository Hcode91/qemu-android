# Android native boot test on the Galaxy S23

This guest runs real Android 4.1.2 init and a native test service, prints its
results and powers off. It has **no Android home screen or app support**.
It uses an obsolete Linux 3.18 kernel and has not been security-cleared.
Keep networking disabled. No proprietary firmware, games, root or Termux is
required. Do not replace the working Linux VM's files.

Use only an APK whose build includes the **Android 4.1 native boot test** profile
and has passed Android development CI. The previous working APK does not have
this board profile. Disposable CI debug certificates can conflict with an
installed development build; do not uninstall it without preserving your
original downloaded guest files. Uninstalling deletes the app's private VMs
and disks. There is no VM export function yet.

After both the new APK and native-guest workflow pass:

1. Download the APK artifact and the **QEMU-Android-AOSP-native-guest** artifact
   from the relevant Actions runs. Extract the guest ZIP with your phone's
   file manager. `sources/` holds corresponding source code, not import files.
2. In the updated app, select the **Android 4.1 native boot test (no home screen)**
   profile for a new VM. Create a separate VM with **512 MiB RAM, 1 CPU** and
   software display **off**.
3. Select that VM. Import `zImage` using **Import kernel**,
   `native-initramfs.cpio.gz` using **Import initrd**, and
   `vexpress-v2p-ca9.dtb` using **Import dtb**. Do not create/import a disk.
4. Tap **Start**. A blank graphical display is expected: watch the guest serial
   console. It should print init markers, `AOSP-KMSG-DEVICE-OK`, property,
   Binder, ashmem and liblog results, `AOSP-NATIVE-USERSPACE-PASS`, then
   `reboot: Power down`. Host diagnostics should report `QEMU exit status: 0`.
5. Use **Copy all logs** and share that text. Repeat Start once to check that
   the process/socket cleanup permits another boot. Do not interpret this as
   Android framework, UI, app or OUYA compatibility.

The artifact's manifest records the source commit, run, image hashes and
verification scope. SHA256SUMS covers the image and corresponding-source
archives. Host boot success does not establish phone boot success.

## Licences and rebuilding

Linux is GPL-2.0-only; its full corresponding source and config are in
`sources/kernel-corresponding-source.tar.gz` and `sources/kernel.config`.
The kernel/compiler pins are in `sources/sources.json`. AOSP init/native
libraries retain their upstream Apache-2.0/BSD and other notices in
`sources/native-corresponding-sources.tar.gz`. Downstream test code is
GPL-2.0-or-later and is included under `sources/downstream`.

The exact downstream build scripts are also available at
`https://github.com/Hcode91/qemu-android/tree/<source_commit>/android`, using
the source_commit in manifest.json. Run the AOSP native userspace workflow on
that commit to rebuild. See its README and source locks for the legacy toolchain
and archive limitations. This is a research artifact, not a signed Android guest
release or a security clearance.
