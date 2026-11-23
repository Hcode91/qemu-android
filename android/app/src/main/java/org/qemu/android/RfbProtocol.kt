// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Bounded RFB 3.8 raw-pixel client. No guest clipboard integration. */
data class GuestFrame(val width: Int, val height: Int, val pixels: IntArray)

class RfbProtocol(input: InputStream, output: OutputStream) {
    private val reader = DataInputStream(input)
    private val writer = DataOutputStream(output)
    private val writeLock = Any()
    private var width = 0
    private var height = 0
    private var pixels = IntArray(0)

    private fun resize(w: Int, h: Int) {
        require(w in 1..2048 && h in 1..2048 && w.toLong() * h <= 4_194_304) { "Guest display size exceeds limit" }
        width = w; height = h; pixels = IntArray(w * h)
    }
    private fun write(action: (DataOutputStream) -> Unit) = synchronized(writeLock) {
        action(writer); writer.flush()
    }
    fun handshake() {
        val version = ByteArray(12).also { reader.readFully(it) }
        require(String(version, Charsets.US_ASCII) == "RFB 003.008\n") { "Expected QEMU RFB 3.8" }
        write { it.write(version) }
        val count = reader.readUnsignedByte()
        require(count in 1..32) { "VNC security negotiation failed" }
        val types = ByteArray(count).also { reader.readFully(it) }
        require(types.any { it.toInt() == 1 }) { "Private VNC socket must offer no-auth security" }
        write { it.writeByte(1) }
        check(reader.readInt() == 0) { "VNC authentication failed" }
        write { it.writeByte(1) } // shared client
        resize(reader.readUnsignedShort(), reader.readUnsignedShort())
        reader.readFully(ByteArray(16)) // Request our own known pixel format below.
        val nameSize = reader.readInt()
        require(nameSize in 0..4096) { "Oversized VNC server name" }
        reader.readFully(ByteArray(nameSize))
        write {
            it.writeByte(0); it.write(byteArrayOf(0, 0, 0))
            it.writeByte(32); it.writeByte(24); it.writeByte(0); it.writeByte(1)
            repeat(3) { _ -> it.writeShort(255) }
            it.writeByte(16); it.writeByte(8); it.writeByte(0); it.write(byteArrayOf(0, 0, 0))
            it.writeByte(2); it.writeByte(0); it.writeShort(2)
            it.writeInt(0); it.writeInt(-223) // Raw, DesktopSize
        }
        request(false)
    }
    private fun request(incremental: Boolean) = write {
        it.writeByte(3); it.writeByte(if (incremental) 1 else 0)
        it.writeShort(0); it.writeShort(0); it.writeShort(width); it.writeShort(height)
    }
    /** Read one server message. Returns a frame only after server pixel/size updates. */
    fun readMessage(): GuestFrame? {
        when (val type = reader.readUnsignedByte()) {
            0 -> {
                reader.readUnsignedByte()
                val count = reader.readUnsignedShort()
                require(count <= 4096) { "Too many VNC rectangles" }
                var changed = false
                for (index in 0 until count) {
                    val x = reader.readUnsignedShort(); val y = reader.readUnsignedShort()
                    val w = reader.readUnsignedShort(); val h = reader.readUnsignedShort()
                    when (val encoding = reader.readInt()) {
                        -223 -> { require(x == 0 && y == 0); resize(w, h); changed = true }
                        0 -> {
                            require(x + w <= width && y + h <= height) { "VNC rectangle outside framebuffer" }
                            val row = ByteArray(w * 4)
                            for (dy in 0 until h) {
                                reader.readFully(row)
                                for (dx in 0 until w) {
                                    val i = dx * 4
                                    pixels[(y + dy) * width + x + dx] = (0xff shl 24) or
                                        ((row[i + 2].toInt() and 255) shl 16) or
                                        ((row[i + 1].toInt() and 255) shl 8) or (row[i].toInt() and 255)
                                }
                            }
                            changed = changed || (w > 0 && h > 0)
                        }
                        else -> error("Unsupported VNC encoding $encoding")
                    }
                }
                request(true)
                return if (changed) GuestFrame(width, height, pixels.copyOf()) else null
            }
            2 -> Unit // Bell has no payload.
            3 -> {
                reader.readFully(ByteArray(3))
                val size = reader.readInt()
                require(size in 0..65536) { "Guest clipboard message exceeds limit" }
                reader.readFully(ByteArray(size)) // Deliberately never set host clipboard.
            }
            else -> error("Unsupported VNC message $type")
        }
        return null
    }
    fun key(keysym: Int, down: Boolean) = write {
        it.writeByte(4); it.writeByte(if (down) 1 else 0); it.writeShort(0); it.writeInt(keysym)
    }
    fun pointer(x: Int, y: Int, pressed: Boolean) = write {
        require(width > 0 && height > 0)
        it.writeByte(5); it.writeByte(if (pressed) 1 else 0)
        it.writeShort(x.coerceIn(0, width - 1)); it.writeShort(y.coerceIn(0, height - 1))
    }
}
