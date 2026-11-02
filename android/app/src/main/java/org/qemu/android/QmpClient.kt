// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android

import android.net.LocalSocket
import android.net.LocalSocketAddress
import org.json.JSONObject
import java.io.Closeable

class QmpClient(path: String) : Closeable {
    private val socket = LocalSocket()
    private val reader: java.io.BufferedReader
    private val writer: java.io.BufferedWriter
    private var nextId = 0
    init {
        try {
            socket.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
            socket.soTimeout = 3000
            reader = socket.inputStream.bufferedReader()
            writer = socket.outputStream.bufferedWriter()
            check(JSONObject(reader.readLine() ?: error("QMP disconnected")).has("QMP")) { "Invalid QMP greeting" }
            execute("qmp_capabilities")
        } catch (e: Exception) { socket.close(); throw e }
    }
    @Synchronized fun execute(command: String): JSONObject {
        require(command in setOf("qmp_capabilities", "query-status", "stop", "cont", "system_reset", "system_powerdown", "quit"))
        val id = ++nextId
        writer.write(JSONObject().put("execute", command).put("id", id).toString())
        writer.newLine(); writer.flush()
        repeat(256) {
            val reply = JSONObject(reader.readLine() ?: error("QMP disconnected"))
            if (reply.optInt("id", -1) == id) {
                check(!reply.has("error")) { reply.toString() }
                return reply
            }
        }
        error("Too many unsolicited QMP messages")
    }
    override fun close() { socket.close() }
}
