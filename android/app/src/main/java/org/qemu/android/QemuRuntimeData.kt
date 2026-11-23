// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/** Materialize trusted APK assets for QEMU's filesystem-based -L search. */
object QemuRuntimeData {
    fun install(root: File, names: List<String>, open: (String) -> InputStream): File {
        require(names.size in 1..128 && names.distinct().size == names.size)
        require(names.all { it.matches(Regex("[a-z0-9][a-z0-9_-]{0,31}")) }) { "Invalid packaged keymap name" }
        require("en-us" in names) { "Packaged QEMU en-us keymap missing" }
        val keymaps = File(root, "keymaps")
        check(keymaps.isDirectory || keymaps.mkdirs()) { "Cannot create QEMU runtime directory" }
        var total = 0L
        for (name in names.sorted()) {
            val temp = File.createTempFile("keymap-", ".tmp", keymaps)
            try {
                var size = 0
                open(name).use { input -> FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(16384)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count; total += count
                        require(size <= 256 * 1024 && total <= 8L * 1024 * 1024) { "QEMU keymap data exceeds limits" }
                        output.write(buffer, 0, count)
                    }
                    require(size > 0) { "Empty packaged QEMU keymap" }
                    output.fd.sync()
                } }
                check(temp.renameTo(File(keymaps, name))) { "Cannot install QEMU keymap" }
            } finally { temp.delete() }
        }
        return root
    }
}
