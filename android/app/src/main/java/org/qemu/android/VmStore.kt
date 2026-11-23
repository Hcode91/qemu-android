// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.io.Closeable
import java.util.UUID

class VmStore(private val context: Context) {
    private val root = File(context.filesDir, "vms").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("vms", Context.MODE_PRIVATE)
    fun list(): List<VmConfig> {
        val array = JSONArray(preferences.getString("list", "[]"))
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            VmConfig(o.getString("id"), o.getString("name"), o.getInt("ram"), o.getInt("cpus"), o.optBoolean("graphics", false), VmBoard.valueOf(o.optString("board", VmBoard.VIRT.name)))
        }
    }
    fun save(vms: List<VmConfig>) {
        val array = JSONArray()
        vms.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("ram", it.memoryMiB).put("cpus", it.cpus).put("graphics", it.graphics).put("board", it.board.name)) }
        check(preferences.edit().putString("list", array.toString()).commit())
    }
    fun create(name: String, ram: Int, cpus: Int, graphics: Boolean = false, board: VmBoard = VmBoard.VIRT): VmConfig {
        val vm = VmConfig(UUID.randomUUID().toString(), name, ram, cpus, graphics, board)
        directory(vm).mkdirs()
        save(list() + vm)
        return vm
    }
    fun directory(vm: VmConfig) = File(root, vm.id)
    fun acquire(vm: VmConfig): Closeable {
        val file = RandomAccessFile(File(directory(vm), ".lock"), "rw")
        try {
            val lock = file.channel.tryLock() ?: error("VM images are busy")
            return Closeable { lock.release(); file.close() }
        } catch (e: Exception) { file.close(); throw e }
    }
    fun importImage(vm: VmConfig, uri: Uri, slot: String) = acquire(vm).use {
        require(slot in listOf("kernel", "initrd", "disk.raw", "dtb"))
        require(slot != "dtb" || vm.board == VmBoard.AOSP_NATIVE) { "Device tree import is for the Android native profile" }
        require(slot != "disk.raw" || vm.board == VmBoard.VIRT) { "The Android native boot test does not use a disk" }
        val dir = directory(vm)
        val destination = File(dir, slot)
        val temp = File.createTempFile("import-", ".part", dir)
        val cap = when (slot) { "disk.raw" -> 8L shl 30; "dtb" -> GuestDeviceTree.MAX_BYTES; else -> 256L shl 20 }
        try {
            var total = 0L
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open selected document" }
                FileOutputStream(temp).use { output ->
                    val bytes = ByteArray(1024 * 1024)
                    while (true) {
                        val n = input.read(bytes)
                        if (n == -1) break
                        total += n
                        require(total <= cap) { "Image exceeds size limit" }
                        check(dir.usableSpace > n + (64L shl 20)) { "Insufficient free storage" }
                        output.write(bytes, 0, n)
                    }
                    output.fd.sync()
                }
            }
            require(total > 0) { "Empty image" }
            if (slot == "dtb") GuestDeviceTree.validate(temp)
            if (slot == "disk.raw") RandomAccessFile(temp, "r").use {
                if (it.length() >= 4) require(it.readInt() != 0x514649fb) { "QCOW images are unsupported; import a raw image" }
            }
            if (slot == "kernel") RandomAccessFile(temp, "r").use {
                require(it.length() > 0x28) { "Truncated kernel" }
                it.seek(0x24)
                require(Integer.reverseBytes(it.readInt()) == 0x016f2818) { "Expected ARM Linux zImage" }
            }
            check(temp.renameTo(destination)) { "Cannot replace image" }
        } finally { temp.delete() }
    }
    fun createDisk(vm: VmConfig, sizeMiB: Int) = acquire(vm).use {
        require(vm.board == VmBoard.VIRT) { "The Android native boot test does not use a disk" }
        require(sizeMiB in 64..8192)
        val disk = File(directory(vm), "disk.raw")
        check(!disk.exists()) { "Disk already exists; refusing to overwrite" }
        RandomAccessFile(disk, "rw").use { it.setLength(sizeMiB.toLong() shl 20); it.fd.sync() }
    }
}
