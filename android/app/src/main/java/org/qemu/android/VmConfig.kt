// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android

import java.io.File
import java.util.UUID

data class VmConfig(val id: String, val name: String, val memoryMiB: Int = 512, val cpus: Int = 2, val graphics: Boolean = false) {
    init {
        require(UUID.fromString(id).toString() == id)
        require(name.isNotBlank() && name.length <= 80)
        require(memoryMiB in 128..3072)
        require(cpus in 1..4)
    }
    fun arguments(directory: File, qmpPath: String, vncPath: String? = null): List<String> {
        require(File(directory, "kernel").isFile) { "Import an ARMv7 Linux zImage first" }
        require(qmpPath.length < 100 && !qmpPath.contains(','))
        val args = mutableListOf("-no-user-config", "-nodefaults", "-machine", "virt", "-cpu", "cortex-a15",
            "-accel", "tcg,thread=multi,split-wx=on", "-m", memoryMiB.toString(), "-smp", cpus.toString(),
            "-display", "none", "-monitor", "none", "-serial", "stdio", "-nic", "none",
            "-qmp", "unix:$qmpPath,server=on,wait=off", "-kernel", File(directory, "kernel").absolutePath,
            "-append", "console=ttyAMA0 rdinit=/init panic=1")
        if (graphics) {
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
