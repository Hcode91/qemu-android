// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files

class GuestDeviceTreeTest {
    companion object {
        // Minimal FDT with an empty root; a real board DTB comes from kernel CI.
        fun emptyTree(): ByteArray {
            val data = ByteBuffer.allocate(72)
            listOf(0xd00dfeed.toInt(), 72, 56, 72, 40, 17, 16, 0, 0, 16).forEach { data.putInt(it) }
            data.position(56)
            listOf(1, 0, 2, 9).forEach { data.putInt(it) }
            return data.array()
        }
    }
    @Test fun malformedImportsAreRejected() {
        val dir = Files.createTempDirectory("dtb import").toFile()
        val file = File(dir, "tree")
        try {
            file.writeBytes(emptyTree()); GuestDeviceTree.validate(file)
            val invalid = listOf(
                emptyTree().copyOf(39),
                emptyTree().also { it[0] = 0 },
                emptyTree().also { ByteBuffer.wrap(it).putInt(4, 100) },
                emptyTree().also { ByteBuffer.wrap(it).putInt(8, -4) },
                emptyTree().also { ByteBuffer.wrap(it).putInt(32, Int.MAX_VALUE) },
                emptyTree().also { ByteBuffer.wrap(it).putInt(12, 56).putInt(32, 4) },
                emptyTree().also { ByteBuffer.wrap(it).putInt(20, 18) },
                emptyTree().also { ByteBuffer.wrap(it).putInt(16, 44) }
            )
            invalid.forEach { bytes ->
                file.writeBytes(bytes)
                assertThrows(IllegalArgumentException::class.java) { GuestDeviceTree.validate(file) }
            }
        } finally { dir.deleteRecursively() }
    }
}
