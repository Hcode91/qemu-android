// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.*
import org.junit.Test

class RfbProtocolTest {
    private fun server(width: Int = 2, height: Int = 1, messages: (DataOutputStream) -> Unit = {}): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            write("RFB 003.008\n".toByteArray()); writeByte(1); writeByte(1); writeInt(0)
            writeShort(width); writeShort(height); write(ByteArray(16)); writeInt(0)
            messages(this)
        }
        return bytes.toByteArray()
    }
    private fun update(out: DataOutputStream, x: Int, y: Int, w: Int, h: Int, encoding: Int, data: ByteArray = byteArrayOf()) {
        out.writeByte(0); out.writeByte(0); out.writeShort(1)
        out.writeShort(x); out.writeShort(y); out.writeShort(w); out.writeShort(h); out.writeInt(encoding); out.write(data)
    }
    @Test fun rawPixelsAndPartialUpdatesPreserveImmutableSnapshots() {
        val input = server { out ->
            update(out, 0, 0, 2, 1, 0, byteArrayOf(0, 0, -1, 0, 0, -1, 0, 0))
            update(out, 1, 0, 1, 1, 0, byteArrayOf(-1, 0, 0, 0))
        }
        val protocol = RfbProtocol(ByteArrayInputStream(input), ByteArrayOutputStream())
        protocol.handshake()
        val first = protocol.readMessage()!!
        assertArrayEquals(intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt()), first.pixels)
        val second = protocol.readMessage()!!
        assertArrayEquals(intArrayOf(0xffff0000.toInt(), 0xff0000ff.toInt()), second.pixels)
        assertEquals(0xff00ff00.toInt(), first.pixels[1])
    }
    @Test fun rejectsOversizedDisplaysAndOutOfBoundsRectangles() {
        assertThrows(IllegalArgumentException::class.java) {
            RfbProtocol(ByteArrayInputStream(server(4096, 4096)), ByteArrayOutputStream()).handshake()
        }
        val input = server { update(it, 1, 0, 2, 1, 0) }
        val protocol = RfbProtocol(ByteArrayInputStream(input), ByteArrayOutputStream()); protocol.handshake()
        assertThrows(IllegalArgumentException::class.java) { protocol.readMessage() }
    }
    @Test fun resizeUpdatesDimensionsAndInputUsesNetworkByteOrder() {
        val input = server { update(it, 0, 0, 3, 2, -223) }
        val output = ByteArrayOutputStream()
        val protocol = RfbProtocol(ByteArrayInputStream(input), output); protocol.handshake()
        assertEquals(3, protocol.readMessage()!!.width)
        output.reset(); protocol.key(0xff0d, true); protocol.pointer(999, -1, true)
        assertArrayEquals(byteArrayOf(4, 1, 0, 0, 0, 0, -1, 13, 5, 1, 0, 2, 0, 0), output.toByteArray())
    }
    @Test fun rejectsUnknownEncoding() {
        val protocol = RfbProtocol(ByteArrayInputStream(server { update(it, 0, 0, 1, 1, 999) }), ByteArrayOutputStream())
        protocol.handshake()
        assertThrows(IllegalStateException::class.java) { protocol.readMessage() }
    }
}
