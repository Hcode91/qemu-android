// SPDX-License-Identifier: GPL-2.0-or-later
package org.qemu.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Software canvas over actual RFB pixels. Controller sticks map to guest arrow keys. */
class GuestDisplayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    var sendKey: (Int, Boolean) -> Unit = { _, _ -> }
    var sendPointer: (Int, Int, Boolean) -> Unit = { _, _, _ -> }
    private var frame: GuestFrame? = null
    private var bitmap: Bitmap? = null
    private val destination = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val held = mutableMapOf<Int, Int>()
    private var sent = emptySet<Int>()
    private var axes = emptySet<Int>()
    init {
        holder.addCallback(this)
        isFocusable = true; isFocusableInTouchMode = true
        contentDescription = "Actual guest display. Tap for touch, keyboard and controller input."
    }
    fun showFrame(next: GuestFrame?) {
        if (next === frame) return
        if (next == null) {
            releaseKeys()
            frame = null; bitmap = null; destination.setEmpty(); drawGuest()
            return
        }
        frame = next
        var image = bitmap
        if (image == null || image.width != next.width || image.height != next.height) {
            image = Bitmap.createBitmap(next.width, next.height, Bitmap.Config.ARGB_8888)
            bitmap = image
        }
        image.setPixels(next.pixels, 0, next.width, 0, 0, next.width, next.height)
        drawGuest()
    }
    private fun drawGuest() {
        if (!holder.surface.isValid) return
        val canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
        try {
            canvas.drawColor(Color.BLACK)
            bitmap?.let { image ->
                val scale = minOf(width.toFloat() / image.width, height.toFloat() / image.height)
                val w = image.width * scale; val h = image.height * scale
                destination.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
                canvas.drawBitmap(image, null, destination, paint)
            }
        } finally { runCatching { holder.unlockCanvasAndPost(canvas) } }
    }
    override fun surfaceCreated(holder: SurfaceHolder) { drawGuest() }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { drawGuest() }
    override fun surfaceDestroyed(holder: SurfaceHolder) { releaseKeys() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val current = frame ?: return false
        if (destination.width() <= 0 || destination.height() <= 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) { requestFocus(); parent?.requestDisallowInterceptTouchEvent(true) }
                val x = ((event.x - destination.left) * current.width / destination.width()).toInt()
                val y = ((event.y - destination.top) * current.height / destination.height()).toInt()
                sendPointer(x.coerceIn(0, current.width - 1), y.coerceIn(0, current.height - 1), true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val x = ((event.x - destination.left) * current.width / destination.width()).toInt()
                val y = ((event.y - destination.top) * current.height / destination.height()).toInt()
                sendPointer(x.coerceIn(0, current.width - 1), y.coerceIn(0, current.height - 1), false)
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
            else -> return false
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    private fun keysym(event: KeyEvent): Int? = when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> 0xff51
        KeyEvent.KEYCODE_DPAD_UP -> 0xff52
        KeyEvent.KEYCODE_DPAD_RIGHT -> 0xff53
        KeyEvent.KEYCODE_DPAD_DOWN -> 0xff54
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_START -> 0xff0d
        KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B -> 0xff1b
        KeyEvent.KEYCODE_DEL -> 0xff08
        KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_BUTTON_SELECT -> 0xff09
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> 0xffe1
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> 0xffe3
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> 0xffe9
        KeyEvent.KEYCODE_BUTTON_X -> 'x'.code
        KeyEvent.KEYCODE_BUTTON_Y -> 'y'.code
        KeyEvent.KEYCODE_BUTTON_L1 -> 'q'.code
        KeyEvent.KEYCODE_BUTTON_R1 -> 'e'.code
        else -> event.unicodeChar.takeIf { it in 32..0x10ffff }?.let { if (it < 256) it else 0x01000000 or it }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val symbol = keysym(event) ?: return super.onKeyDown(keyCode, event)
        if (!held.containsKey(keyCode)) { held[keyCode] = symbol; updateKeys() }
        return true
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (held.remove(keyCode) == null) return super.onKeyUp(keyCode, event)
        updateKeys()
        return true
    }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and android.view.InputDevice.SOURCE_JOYSTICK) != android.view.InputDevice.SOURCE_JOYSTICK) return super.onGenericMotionEvent(event)
        val x = event.getAxisValue(MotionEvent.AXIS_X) + event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y) + event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val next = mutableSetOf<Int>()
        if (x < -0.5f) next.add(0xff51)
        if (x > 0.5f) next.add(0xff53)
        if (y < -0.5f) next.add(0xff52)
        if (y > 0.5f) next.add(0xff54)
        axes = next
        updateKeys()
        return true
    }
    private fun updateKeys() {
        val next = held.values.toSet() + axes
        (sent - next).forEach { sendKey(it, false) }
        (next - sent).forEach { sendKey(it, true) }
        sent = next
    }
    private fun releaseKeys() {
        held.clear(); axes = emptySet(); updateKeys()
    }
    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus) releaseKeys()
    }
}
