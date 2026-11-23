// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Bound foreground UI session. Closing the UI stops QEMU; no unattended background VM. */
class VmService : Service() {
    inner class LocalBinder : Binder() { fun service() = this@VmService }
    private val commands = Executors.newSingleThreadExecutor()
    private val io = Executors.newCachedThreadPool()
    private val displayInput = Executors.newSingleThreadExecutor()
    @Volatile private var display: RfbConnection? = null
    @Volatile private var latestFrame: GuestFrame? = null
    fun frame() = latestFrame
    private var process: Process? = null
    private var qmp: QmpClient? = null
    private var imageLock: java.io.Closeable? = null
    @Volatile var activeId: String? = null
        private set
    private val serial = StringBuilder()
    private val diagnostics = StringBuilder()
    override fun onBind(intent: Intent): IBinder = LocalBinder()
    private fun append(target: StringBuilder, value: String): Unit = synchronized(target) {
        target.append(value)
        if (target.length > 131072) target.delete(0, target.length - 131072)
    }
    fun serialText() = synchronized(serial) { serial.toString() }
    fun diagnosticText() = synchronized(diagnostics) { diagnostics.toString() }
    @Synchronized fun start(vm: VmConfig) {
        check(activeId == null) { "Stop the current VM first" }
        activeId = vm.id
        commands.execute {
        try {
            imageLock = VmStore(this).acquire(vm)
            val executable = File(applicationInfo.nativeLibraryDir, "libqemu-system-arm.so")
            check(executable.canExecute()) { "Packaged QEMU executable unavailable" }
            val socket = File(filesDir, "qmp.sock").apply { delete() }
            val displaySocket = File(filesDir, "vnc.sock").apply { delete() }
            latestFrame = null
            val dir = VmStore(this).directory(vm)
            val data = QemuRuntimeData.install(File(filesDir, "qemu-data"),
                assets.list("qemu/keymaps")?.toList() ?: emptyList()) { assets.open("qemu/keymaps/$it") }
            val p = ProcessBuilder(listOf(executable.absolutePath) + vm.arguments(dir, socket.absolutePath, displaySocket.absolutePath, data.absolutePath))
                .directory(dir).apply { environment()["TMPDIR"] = cacheDir.absolutePath }.start()
            process = p; activeId = vm.id
            synchronized(serial) { serial.clear() }
            consume(p.inputStream, serial); consume(p.errorStream, diagnostics)
            // Poll only the private socket and fail if native process exits.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!socket.exists() && p.isAlive && System.nanoTime() < deadline) Thread.sleep(25)
            check(p.isAlive && socket.exists()) { "QEMU exited or QMP startup timed out" }
            qmp = QmpClient(socket.absolutePath)
            append(diagnostics, "QMP: ${qmp!!.execute("query-status")}\n")
            if (vm.graphics) io.execute {
                var connection: RfbConnection? = null
                try {
                    val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                    while (!displaySocket.exists() && p.isAlive && System.nanoTime() < until) Thread.sleep(25)
                    check(p.isAlive && displaySocket.exists()) { "Display socket startup timed out" }
                    val active = RfbConnection(displaySocket.absolutePath)
                    connection = active
                    display = active
                    append(diagnostics, "Display: private RFB connected (software rendering)\n")
                    while (p.isAlive) active.nextFrame()?.let { latestFrame = it }
                } catch (e: Exception) {
                    if (p.isAlive) append(diagnostics, "Display error: ${e.message}\n")
                } finally { connection?.close(); if (display === connection) display = null }
            }
            io.execute {
                val code = p.waitFor()
                runCatching { commands.execute {
                    if (process === p) { display?.close(); display = null; qmp?.close(); qmp = null; process = null; imageLock?.close(); imageLock = null; activeId = null }
                    append(diagnostics, "QEMU exit status: $code\n")
                } } // Service teardown can close the command executor before this callback.
            }
        } catch (e: Exception) {
            append(diagnostics, "Host: ${e.message}\n")
            terminate()
        }
    }
    }
    private fun consume(input: InputStream, target: StringBuilder) = io.execute {
        try { input.reader().use { reader ->
            val buffer = CharArray(4096)
            while (true) { val n = reader.read(buffer); if (n < 0) break; append(target, String(buffer, 0, n)) }
        } } catch (e: Exception) { append(diagnostics, "Host stream closed: ${e.message}\n") }
    }
    fun command(command: String) = commands.execute {
        try { append(diagnostics, "QMP: ${checkNotNull(qmp) { "VM not running" }.execute(command)}\n") }
        catch (e: Exception) { append(diagnostics, "Host control error: ${e.message}\n") }
    }
    fun input(text: String) = commands.execute {
        try { checkNotNull(process).outputStream.apply { write((text + "\n").toByteArray()); flush() } }
        catch (e: Exception) { append(diagnostics, "Host input error: ${e.message}\n") }
    }
    fun displayKey(keysym: Int, down: Boolean) = displayInput.execute {
        try { display?.key(keysym, down) } catch (e: Exception) { append(diagnostics, "Display input: ${e.message}\n") }
    }
    fun displayPointer(x: Int, y: Int, pressed: Boolean) = displayInput.execute {
        try { display?.pointer(x, y, pressed) } catch (e: Exception) { append(diagnostics, "Display input: ${e.message}\n") }
    }
    fun forceStop() = commands.execute { terminate() }
    private fun terminate() {
        display?.close(); display = null
        qmp?.close(); qmp = null
        process?.let { p ->
            p.destroy()
            if (!p.waitFor(2, TimeUnit.SECONDS)) { p.destroyForcibly(); p.waitFor(2, TimeUnit.SECONDS) }
        }
        process = null; imageLock?.close(); imageLock = null; activeId = null
    }
    override fun onDestroy() {
        commands.execute { terminate(); io.shutdown(); displayInput.shutdown(); commands.shutdown() }
        super.onDestroy()
    }
}
