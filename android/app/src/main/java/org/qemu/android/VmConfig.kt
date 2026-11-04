// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android

import java.io.File
import java.util.UUID

enum class VmBoard(val label: String, val machine: String, val cpu: String) {
    VIRT("Linux virt", "virt", "cortex-a15"),
    AOSP_NATIVE("Android 4.1 native boot test (no home screen)", "vexpress-a9", "cortex-a9")
}

data class VmConfig(val id: String, val name: String, val memoryMiB: Int = 512, val cpus: Int = 2, val graphics: Boolean = false, val board: VmBoard = VmBoard.VIRT) {
    init {
        require(UUID.fromString(id).toString() == id)
        require(name.isNotBlank() && name.length <= 80)
        require(memoryMiB in 128..3072)
        require(cpus in 1..4)
        if (board == VmBoard.AOSP_NATIVE) {
            require(memoryMiB == 512 && cpus == 1) { "Android native boot test requires 512 MiB and 1 CPU" }
            require(!graphics) { "Android native boot test uses the serial console only" }
        }
    }
    fun arguments(directory: File, qmpPath: String, vncPath: String? = null, dataPath: String? = null): List<String> {
        require(File(directory, "kernel").isFile) { "Import an ARMv7 Linux zImage first" }
        require(qmpPath.length < 100 && !qmpPath.contains(','))
        val acceleration = if (board == VmBoard.VIRT) "tcg,thread=multi,split-wx=on" else "tcg,thread=single,split-wx=on"
        val args = mutableListOf("-no-user-config", "-nodefaults", "-machine", board.machine, "-cpu", board.cpu,
            "-accel", acceleration, "-m", memoryMiB.toString(), "-smp", cpus.toString(),
            "-display", "none", "-monitor", "none", "-serial", "stdio", "-nic", "none",
            "-qmp", "unix:$qmpPath,server=on,wait=off", "-kernel", File(directory, "kernel").absolutePath,
            "-append", "console=ttyAMA0 rdinit=/init panic=1")
        if (board == VmBoard.AOSP_NATIVE) {
            require(File(directory, "initrd").isFile) { "Import the AOSP native initramfs first" }
            val dtb = File(directory, "dtb")
            require(dtb.isFile) { "Import vexpress-v2p-ca9.dtb first" }
            GuestDeviceTree.validate(dtb)
            require(!File(directory, "disk.raw").exists()) { "The native boot test does not use a disk; create a separate VM" }
            args += listOf("-dtb", dtb.absolutePath, "-no-reboot")
            // The board always creates PL041, even for this serial-only guest.
            // -nodefaults suppresses implicit audio: bind it to an explicit sink.
            args[args.indexOf("-machine") + 1] = "vexpress-a9,audiodev=aosp-silent"
            args += listOf("-audiodev", "none,id=aosp-silent")
            args[args.indexOf("-append") + 1] += " androidboot.console=ttyAMA0 androidboot.hardware=qemu_android_native"
        }
        if (dataPath != null) {
            require(dataPath.isNotBlank() && File(dataPath).isAbsolute && !dataPath.contains('\u0000')) { "Private QEMU data directory required" }
            args += listOf("-L", dataPath)
        }
        if (graphics) {
            require(dataPath != null) { "Graphical QEMU requires packaged runtime data" }
            require(vncPath != null && vncPath.length < 100 && !vncPath.contains(',')) { "Private display socket required" }
            args[args.indexOf("-append") + 1] = "console=tty0 console=ttyAMA0 rdinit=/init panic=1 qemu_android.display_test=1"
            // GPU and input drivers require VIRTIO_F_VERSION_1. The MMIO
            // transport defaults to legacy on virt, including our QEMU base.
            args += listOf("-global", "virtio-mmio.force-legacy=false",
                "-vnc", "unix:$vncPath", "-device", "virtio-gpu-device",
                "-device", "virtio-keyboard-device", "-device", "virtio-tablet-device")
        }
        if (File(directory, "initrd").isFile) args += listOf("-initrd", File(directory, "initrd").absolutePath)
        if (File(directory, "disk.raw").isFile) {
            // Fixed private filename, explicit raw format: never follow qcow backing files.
            args += listOf("-drive", "if=none,id=disk0,format=raw,file=${File(directory, "disk.raw").absolutePath}",
                "-device", "virtio-blk-device,drive=disk0")
        }
        return args
    }
}
