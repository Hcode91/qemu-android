// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class QemuRuntimeDataTest {
    @Test fun installsExactMapsInQemuSearchLayoutAndRefreshesOldData() {
        val root = Files.createTempDirectory("qemu data with spaces").toFile()
        try {
            val maps = mapOf("en-us" to "Return 0x1c\na 0x1e\n", "en-gb" to "Return 0x1c\n")
            val installed = QemuRuntimeData.install(root, maps.keys.toList()) { maps.getValue(it).byteInputStream() }
            assertEquals(root, installed)
            assertEquals(maps.getValue("en-us"), File(root, "keymaps/en-us").readText())
            assertEquals(maps.getValue("en-gb"), File(root, "keymaps/en-gb").readText())
            QemuRuntimeData.install(root, listOf("en-us")) { "new-map".byteInputStream() }
            assertEquals("new-map", File(root, "keymaps/en-us").readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun rejectsMissingDefaultAndUnsafeNamesBeforeOpeningAssets() {
        val root = Files.createTempDirectory("qemu runtime rejection").toFile()
        try {
            for (names in listOf(listOf("en-gb"), listOf("en-us", "../escape"), listOf("en-us", "en-us"))) {
                assertThrows(IllegalArgumentException::class.java) {
                    QemuRuntimeData.install(root, names) { error("Unsafe manifest must not open assets") }
                }
            }
            assertFalse(File(root, "keymaps").exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun failedCopyKeepsPreviousMapAndRemovesTemporaryFiles() {
        val root = Files.createTempDirectory("qemu runtime atomic").toFile()
        try {
            QemuRuntimeData.install(root, listOf("en-us")) { "working-map".byteInputStream() }
            for (bytes in listOf(ByteArray(0), ByteArray(256 * 1024 + 1))) {
                assertThrows(IllegalArgumentException::class.java) {
                    QemuRuntimeData.install(root, listOf("en-us")) { ByteArrayInputStream(bytes) }
                }
                assertEquals("working-map", File(root, "keymaps/en-us").readText())
                assertEquals(listOf("en-us"), File(root, "keymaps").list()!!.sorted())
            }
        } finally { root.deleteRecursively() }
    }
}
