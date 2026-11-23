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
}
