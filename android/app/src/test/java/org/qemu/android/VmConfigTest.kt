// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File

class VmConfigTest {
    private val id = "12345678-1234-1234-1234-123456789abc"
    @Test fun unsafeConfigurationsAreRejected() {
        listOf(0, 127, 3073).forEach { ram -> assertThrows(IllegalArgumentException::class.java) { VmConfig(id, "Linux", ram) } }
        assertThrows(IllegalArgumentException::class.java) { VmConfig("../escape", "Linux") }
        assertThrows(IllegalArgumentException::class.java) { VmConfig(id, "Linux", cpus = 8) }
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
}
