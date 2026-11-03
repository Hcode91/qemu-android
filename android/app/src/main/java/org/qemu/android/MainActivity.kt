// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.*
import java.util.concurrent.Executors

/** Real guest serial and optional software display over private sockets. */
class MainActivity : Activity() {
    private lateinit var store: VmStore
    private lateinit var layout: LinearLayout
    private lateinit var chooser: Spinner
    private lateinit var serial: TextView
    private lateinit var diagnostics: TextView
    private lateinit var state: TextView
    private lateinit var displayView: GuestDisplayView
    private var refreshCount = 0
    private var vms = emptyList<VmConfig>()
    private var service: VmService? = null
    private var importId: String? = null
    private var importSlot: String? = null
    private var importing = false
    private var bound = false
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) { service = (binder as VmService.LocalBinder).service() }
        override fun onServiceDisconnected(name: ComponentName) { service = null }
    }
    private val refresh = object : Runnable {
        override fun run() {
            service?.let {
                displayView.showFrame(it.frame())
                if (refreshCount++ % 5 == 0) {
                    serial.text = it.serialText(); diagnostics.text = it.diagnosticText()
                    state.text = "Running VM: ${it.activeId ?: "none"}"
                }
            }
            handler.postDelayed(this, 100)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importId = savedInstanceState?.getString("importId")
        importSlot = savedInstanceState?.getString("importSlot")
        store = VmStore(this)
        layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        setContentView(ScrollView(this).apply { addView(layout) })
        label("QEMU-Android — experimental development")
        label("ARMv7 / TCG. Linux virt supports the graphical fixture. Android native boot test runs early init and shuts down; it has no Android home screen. Closing this screen stops the VM. Audio and networking are pending.")
        chooser = Spinner(this); layout.addView(chooser); reload()
        val name = edit("VM name", "Linux")
        val ram = edit("RAM MiB (128–3072)", "512")
        val cpus = edit("CPUs (1–4)", "2")
        label("Profile for a new VM")
        val profile = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, VmBoard.values().map { it.label }) }
        layout.addView(profile)
        profile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (VmBoard.values()[position] == VmBoard.AOSP_NATIVE) { ram.setText("512"); cpus.setText("1") }
                else cpus.setText("2")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        val graphics = CheckBox(this).apply { text = "Software guest display and input (Linux virt)" }
        layout.addView(graphics)
        chooser.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val vm = vms.getOrNull(position)
                graphics.isChecked = vm?.graphics ?: false
                graphics.isEnabled = vm?.board != VmBoard.AOSP_NATIVE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { graphics.isChecked = false }
        }
        button("Create VM") {
            val board = VmBoard.values()[profile.selectedItemPosition]
            store.create(name.text.toString(), ram.text.toString().toInt(), cpus.text.toString().toInt(), graphics.isChecked && board == VmBoard.VIRT, board); reload(); chooser.setSelection(vms.lastIndex)
        }
        listOf("kernel", "initrd", "dtb", "disk.raw").forEach { slot -> button("Import $slot") {
            checkIdle(); check(!importing) { "Import in progress" }
            importId = selected().id; importSlot = slot
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 100)
        } }
        val size = edit("New blank raw disk MiB", "256")
        button("Create blank disk (does not format)") { checkIdle(); store.createDisk(selected(), size.text.toString().toInt()) }
        button("Start") {
            check(!importing) { "Import in progress" }
            val current = checkNotNull(service) { "Service connecting" }
            check(current.activeId == null) { "Stop the current VM first" }
            val vm = selected().copy(graphics = graphics.isChecked)
            store.save(store.list().map { if (it.id == vm.id) vm else it })
            vms = store.list()
            current.start(vm)
        }
        state = label("Service connecting")
        listOf("Pause" to "stop", "Resume" to "cont", "Reset" to "system_reset", "Guest shutdown" to "system_powerdown", "Stop QEMU" to "quit")
            .forEach { (title, command) -> button(title) { checkNotNull(service).command(command) } }
        button("Force stop (may corrupt guest disk)") { checkNotNull(service).forceStop() }
        label("Copy logs")
        label("Copies a snapshot while the VM runs. Each stream retains its latest 128 Ki characters.")
        button("Copy last 100 guest lines") {
            val current = checkNotNull(service) { "Service connecting" }
            copyLog("Guest console (last 100 lines)", current.serialText().lines().takeLast(100).joinToString("\n"))
        }
        button("Copy guest console") {
            val current = checkNotNull(service) { "Service connecting" }
            copyLog("Guest serial console", current.serialText())
        }
        button("Copy host diagnostics") {
            val current = checkNotNull(service) { "Service connecting" }
            copyLog("Host diagnostics / QMP", current.diagnosticText())
        }
        button("Copy all logs") {
            val current = checkNotNull(service) { "Service connecting" }
            val guest = current.serialText()
            val host = current.diagnosticText()
            check(guest.isNotEmpty() || host.isNotEmpty()) { "No logs to copy yet" }
            copyLog("QEMU-Android logs", "Guest serial console\n$guest\n\nHost diagnostics / QMP\n$host")
        }
        label("Guest display (software)")
        label("Enable the display option before Start. Use the graphics-capable Linux fixture. Tap the display to focus keyboard/controller controls.")
        displayView = GuestDisplayView(this).apply {
            sendKey = { symbol, down -> service?.displayKey(symbol, down); Unit }
            sendPointer = { x, y, down -> service?.displayPointer(x, y, down); Unit }
        }
        layout.addView(displayView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (320 * resources.displayMetrics.density).toInt()))
        button("Focus guest display") { displayView.requestFocus() }
        label("Guest serial console")
        serial = label("")
        val input = edit("Serial input", "")
        button("Send serial line") { checkNotNull(service).input(input.text.toString()); input.setText("") }
        label("Host diagnostics / QMP (not guest output)")
        diagnostics = label("")
        bound = bindService(Intent(this, VmService::class.java), connection, Context.BIND_AUTO_CREATE)
    }
    private fun copyLog(title: String, text: String) {
        check(text.isNotEmpty()) { "No logs to copy yet" }
        val clipboard = checkNotNull(getSystemService(ClipboardManager::class.java)) { "Clipboard unavailable" }
        clipboard.setPrimaryClip(ClipData.newPlainText(title, text))
        Toast.makeText(this, "Copied $title", Toast.LENGTH_SHORT).show()
    }
    private fun reload() {
        vms = store.list()
        chooser.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, vms.map { "${it.name} — ${it.board.label} (${it.memoryMiB} MiB, ${it.cpus} CPUs)" })
    }
    private fun selected() = vms.getOrNull(chooser.selectedItemPosition) ?: error("Create a VM first")
    private fun checkIdle() { check(service != null && service?.activeId == null) { "Stop VM before modifying images" }; check(!importing) { "Import in progress" } }
    private fun label(text: String) = TextView(this).apply { this.text = text; setTextIsSelectable(true); this@MainActivity.layout.addView(this) }
    private fun edit(hint: String, value: String) = EditText(this).apply { this.hint = hint; setText(value); this@MainActivity.layout.addView(this) }
    private fun button(title: String, action: () -> Unit) = Button(this).apply {
        text = title; this@MainActivity.layout.addView(this); setOnClickListener { try { action() } catch (e: Exception) { Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show() } }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 100 || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        val vm = store.list().find { it.id == importId } ?: return
        val slot = importSlot ?: return
        try { checkIdle() } catch (e: Exception) { Toast.makeText(this, e.message, Toast.LENGTH_LONG).show(); return }
        importing = true
        worker.execute {
            val result = runCatching { store.importImage(vm, uri, slot) }
            runOnUiThread { importing = false; Toast.makeText(this, result.exceptionOrNull()?.message ?: "Image imported", Toast.LENGTH_LONG).show() }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("importId", importId); outState.putString("importSlot", importSlot)
        super.onSaveInstanceState(outState)
    }
    override fun onStart() { super.onStart(); handler.post(refresh) }
    override fun onStop() { handler.removeCallbacks(refresh); super.onStop() }
    override fun onDestroy() { if (bound) unbindService(connection); worker.shutdown(); super.onDestroy() }
}
