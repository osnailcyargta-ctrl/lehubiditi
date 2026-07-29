package com.blockforge.engine.runtime

import android.view.KeyEvent

/**
 * Unified input for the eight logical keys the block set exposes. Touch buttons, a hardware
 * keyboard and a gamepad all funnel into the same set, so `tombol kanan ditekan` is true no matter
 * which one the player used.
 */
class InputState {

    private val held = HashSet<String>()
    private val pressedThisFrame = HashSet<String>()
    private val releasedThisFrame = HashSet<String>()

    var pointerX: Float = 0f
        private set
    var pointerY: Float = 0f
        private set
    var pointerDown: Boolean = false
        private set
    var pointerJustDown: Boolean = false
        private set
    var pointerJustUp: Boolean = false
        private set

    fun press(key: String) {
        val k = key.uppercase()
        if (held.add(k)) pressedThisFrame.add(k)
    }

    fun release(key: String) {
        val k = key.uppercase()
        if (held.remove(k)) releasedThisFrame.add(k)
    }

    fun setPointer(x: Float, y: Float, down: Boolean) {
        pointerX = x
        pointerY = y
        if (down && !pointerDown) pointerJustDown = true
        if (!down && pointerDown) pointerJustUp = true
        pointerDown = down
    }

    fun isHeld(key: String): Boolean =
        if (key.equals("ANY", true)) held.isNotEmpty() else held.contains(key.uppercase())

    fun justPressed(key: String): Boolean =
        if (key.equals("ANY", true)) pressedThisFrame.isNotEmpty() else pressedThisFrame.contains(key.uppercase())

    fun justReleased(key: String): Boolean =
        if (key.equals("ANY", true)) releasedThisFrame.isNotEmpty() else releasedThisFrame.contains(key.uppercase())

    fun pressedKeys(): Set<String> = pressedThisFrame.toSet()

    fun releasedKeys(): Set<String> = releasedThisFrame.toSet()

    /** Called by the loop after scripts have run — edges last exactly one frame. */
    fun endFrame() {
        pressedThisFrame.clear()
        releasedThisFrame.clear()
        pointerJustDown = false
        pointerJustUp = false
    }

    fun reset() {
        held.clear()
        endFrame()
        pointerDown = false
    }

    companion object {
        /** Maps hardware key codes onto the logical names. Returns null for keys the engine ignores. */
        fun fromKeyCode(code: Int): String? = when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> "LEFT"
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> "RIGHT"
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> "UP"
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> "DOWN"
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_A -> "SPACE"
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_ENTER -> "A"
            KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_BUTTON_B -> "B"
            else -> null
        }
    }
}
