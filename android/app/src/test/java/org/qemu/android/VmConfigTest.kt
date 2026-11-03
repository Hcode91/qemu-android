// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File

class VmConfigTest {
    private val id = "12345678-1234-1234-1234-123456789abc"
    @Test fun unsafeConfigurationsAreRejected() {
        assertEquals(VmBoard.VIRT, VmConfig(id, "Existing Linux").board)
        listOf(0, 127, 3073).forEach { ram -> assertThrows(IllegalArgumentException::class.java) { VmConfig(id, "Linux", ram) } }
        assertThrows(IllegalArgumentException::class.java) { VmConfig("../escape", "Linux") }
        assertThrows(IllegalArgumentException::class.java) { VmConfig(id, "Linux", cpus = 8) }
    }
    @Test fun nativeAndroidProfileRequiresItsBoardFilesAndRejectsVirtDevices() {
        val dir = Files.createTempDirectory("aosp board").toFile()
        try {
            val vm = VmConfig(id, "AOSP native", cpus = 1, board = VmBoard.AOSP_NATIVE)
            File(dir, "kernel").writeBytes(byteArrayOf(1))
            assertThrows(IllegalArgumentException::class.java) { vm.arguments(dir, "/private/qmp") }
            File(dir, "initrd").writeBytes(byteArrayOf(1))
            assertThrows(IllegalArgumentException::class.java) { vm.arguments(dir, "/private/qmp") }
            File(dir, "dtb").writeBytes(GuestDeviceTreeTest.emptyTree())
            val args = vm.arguments(dir, "/private/qmp")
            assertEquals("vexpress-a9", args[args.indexOf("-machine") + 1])
            assertEquals("cortex-a9", args[args.indexOf("-cpu") + 1])
            assertEquals(File(dir, "dtb").absolutePath, args[args.indexOf("-dtb") + 1])
            assertTrue(args.contains("-no-reboot"))
            assertTrue(args.contains("tcg,thread=single,split-wx=on"))
            assertTrue(args[args.indexOf("-append") + 1].contains("androidboot.hardware=qemu_android_native"))
            assertFalse(args.any { it.contains("virtio") || it.contains("kvm") || it.contains("hostfwd") })
            File(dir, "disk.raw").writeBytes(byteArrayOf(0))
            assertThrows(IllegalArgumentException::class.java) { vm.arguments(dir, "/private/qmp") }
            assertThrows(IllegalArgumentException::class.java) { VmConfig(id, "AOSP", cpus = 2, board = VmBoard.AOSP_NATIVE) }
            assertThrows(IllegalArgumentException::class.java) { vm.copy(graphics = true) }
            assertThrows(IllegalArgumentException::class.java) { vm.copy(memoryMiB = 1024) }
        } finally { dir.deleteRecursively() }
    }
    @Test fun rawDiskUsesExplicitFormatAndNoHostSharing() {
        val dir = Files.createTempDirectory("qemu args with spaces").toFile()
        try {
            File(dir, "kernel").writeBytes(byteArrayOf(1))
            File(dir, "disk.raw").writeBytes(byteArrayOf(0))
            val args = VmConfig(id, "Linux").arguments(dir, "/data/user/0/org.qemu.android/qmp.sock")
            assertTrue(args.contains("tcg,thread=multi,split-wx=on"))
            assertTrue(args.contains("if=none,id=disk0,format=raw,file=${File(dir, "disk.raw").absolutePath}"))
            assertFalse(args.any { it.contains("kvm") || it.contains("virtfs") || it.contains("hostfwd") })
            assertEquals("none", args[args.indexOf("-nic") + 1])
        } finally { dir.deleteRecursively() }
    }
    @Test fun graphicalModeIsOptInAndUsesPrivateSocketAndVirtioInput() {
        val dir = Files.createTempDirectory("qemu graphics").toFile()
        try {
            File(dir, "kernel").writeBytes(byteArrayOf(1))
            val vm = VmConfig(id, "Linux", graphics = true)
            assertThrows(IllegalArgumentException::class.java) { vm.arguments(dir, "/private/qmp") }
            assertThrows(IllegalArgumentException::class.java) { vm.arguments(dir, "/private/qmp", "/private/vnc") }
            val args = vm.arguments(dir, "/private/qmp", "/private/vnc", dir.absolutePath)
            assertEquals(dir.absolutePath, args[args.indexOf("-L") + 1])
            assertTrue(args.contains("unix:/private/vnc"))
            assertTrue(args.contains("virtio-gpu-device"))
            assertTrue(args.contains("virtio-keyboard-device"))
            assertTrue(args.contains("virtio-tablet-device"))
            val transport = args.indexOf("-global")
            assertTrue(transport >= 0)
            assertEquals("virtio-mmio.force-legacy=false", args[transport + 1])
            assertTrue(transport < args.indexOf("virtio-gpu-device"))
            val headless = VmConfig(id, "Headless").arguments(dir, "/private/qmp")
            assertFalse(headless.contains("-vnc"))
            assertFalse(headless.contains("virtio-mmio.force-legacy=false"))
        } finally { dir.deleteRecursively() }
    }
}
