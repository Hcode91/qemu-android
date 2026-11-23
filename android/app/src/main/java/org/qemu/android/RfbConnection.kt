// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.Closeable

class RfbConnection(path: String) : Closeable {
    private val socket = LocalSocket()
    private val protocol: RfbProtocol
    init {
        try {
            socket.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
            socket.soTimeout = 10000
            protocol = RfbProtocol(socket.inputStream, socket.outputStream)
            protocol.handshake()
            socket.soTimeout = 0
        } catch (e: Exception) { socket.close(); throw e }
    }
    fun nextFrame() = protocol.readMessage()
    fun key(keysym: Int, down: Boolean) = protocol.key(keysym, down)
    fun pointer(x: Int, y: Int, pressed: Boolean) = protocol.pointer(x, y, pressed)
    override fun close() { socket.close() }
}
