// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android

import java.io.DataInputStream
import java.io.File

/** Bounded FDT header checks before passing a private imported DTB to QEMU. */
object GuestDeviceTree {
    const val MAX_BYTES = 4L * 1024 * 1024
    fun validate(file: File) {
        require(file.length() in 40L..MAX_BYTES) { "Device tree must be 40 bytes to 4 MiB" }
        DataInputStream(file.inputStream()).use { input ->
            require(input.readInt() == 0xd00dfeed.toInt()) { "Expected a flattened device tree (DTB)" }
            val total = input.readInt().toLong() and 0xffffffffL
            val structure = input.readInt().toLong() and 0xffffffffL
            val strings = input.readInt().toLong() and 0xffffffffL
            val reservations = input.readInt().toLong() and 0xffffffffL
            val version = input.readInt()
            val compatible = input.readInt()
            input.readInt() // boot_cpuid_phys
            val stringBytes = input.readInt().toLong() and 0xffffffffL
            val structureBytes = input.readInt().toLong() and 0xffffffffL
            require(total == file.length() && version == 17 && compatible in 0..17) { "Unsupported or truncated DTB" }
            require(structure >= 40 && structure % 4 == 0L && structureBytes >= 4 && structure + structureBytes <= total) { "Invalid DTB structure bounds" }
            require(strings >= 40 && strings + stringBytes <= total) { "Invalid DTB strings bounds" }
            require(reservations >= 40 && reservations % 8 == 0L && reservations + 16 <= total) { "Invalid DTB reservation bounds" }
            require(structure + structureBytes <= strings || strings + stringBytes <= structure) { "Overlapping DTB blocks" }
        }
    }
}
