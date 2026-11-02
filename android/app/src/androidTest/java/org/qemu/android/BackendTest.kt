// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android
import android.test.InstrumentationTestCase
import java.io.File
import java.util.concurrent.TimeUnit

/** Requires an ARM64 Android device/runner. Version execution is not guest boot evidence. */
@Suppress("DEPRECATION")
class BackendTest : InstrumentationTestCase() {
    fun testPackagedBackendExecutes() {
        val file = File(instrumentation.targetContext.applicationInfo.nativeLibraryDir, "libqemu-system-arm.so")
        assertTrue(file.canExecute())
        val process = ProcessBuilder(file.absolutePath, "--version").redirectErrorStream(true).start()
        assertTrue(process.waitFor(15, TimeUnit.SECONDS))
        val text = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.exitValue())
        assertTrue(text.contains("QEMU emulator version"))
    }
    /** Runs only on an ARM64 Android runner; no guest boot is implied. */
    fun testPackagedRuntimeDataAllowsVncStartup() {
        val context = instrumentation.targetContext
        val directory = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "vnc-").toFile()
        val data = QemuRuntimeData.install(File(directory, "data"),
            context.assets.list("qemu/keymaps")?.toList() ?: emptyList()) { context.assets.open("qemu/keymaps/$it") }
        val backend = File(context.applicationInfo.nativeLibraryDir, "libqemu-system-arm.so")
        val vnc = File(directory, "vnc.sock")
        val qmp = File(directory, "qmp.sock")
        val log = File(directory, "backend.log")
        val process = ProcessBuilder(backend.absolutePath, "-L", data.absolutePath,
            "-machine", "virt", "-cpu", "cortex-a15", "-accel", "tcg,split-wx=on",
            "-m", "128", "-S", "-nodefaults", "-display", "none", "-monitor", "none",
            "-serial", "none", "-nic", "none", "-global", "virtio-mmio.force-legacy=false",
            "-device", "virtio-gpu-device", "-vnc", "unix:${vnc.absolutePath}",
            "-qmp", "unix:${qmp.absolutePath},server=on,wait=off")
            .redirectErrorStream(true).redirectOutput(log).start()
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (process.isAlive && (!vnc.exists() || !qmp.exists()) && System.nanoTime() < deadline) Thread.sleep(25)
            assertTrue(log.readText(), process.isAlive && vnc.exists() && qmp.exists())
            QmpClient(qmp.absolutePath).use { control ->
                RfbConnection(vnc.absolutePath).use {
                    assertFalse(control.execute("query-status").getJSONObject("return").getBoolean("running"))
                }
                control.execute("quit")
            }
            assertTrue(process.waitFor(10, TimeUnit.SECONDS))
            assertEquals(log.readText(), 0, process.exitValue())
        } finally {
            if (process.isAlive) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS) }
            directory.deleteRecursively()
        }
    }

}
