package com.blockforge.engine

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.blockforge.engine.model.GameProject
import com.blockforge.engine.render.Renderer
import com.blockforge.engine.runtime.AudioBus
import com.blockforge.engine.runtime.BitmapCache
import com.blockforge.engine.runtime.GameHost
import com.blockforge.engine.runtime.GameWorld
import com.blockforge.engine.runtime.InputState
import com.blockforge.engine.runtime.ResourceProvider

/**
 * Self-contained player: hand it a [GameProject] and a [ResourceProvider] and it runs the game.
 *
 * The editor embeds this for its Play tab and an exported APK sets it as its content view, so the
 * game a creator tests is byte-for-byte the game their players get.
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    val input = InputState()

    var world: GameWorld? = null
        private set

    private var renderer: Renderer? = null
    private var audio: AudioBus? = null
    private var bitmaps: BitmapCache? = null
    private var loop: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    private val pointerKeys = HashMap<Int, String>()
    private val worldPoint = PointF()
    private var fps = 0f

    /** Live block highlighting for the editor. Ignored by exported games. */
    var onFrame: ((Set<String>) -> Unit)? = null

    var host: GameHost? = null
        set(value) {
            field = value
            world?.host = value
        }

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    fun load(project: GameProject, provider: ResourceProvider) {
        stopLoop()
        audio?.release()
        bitmaps?.clear()

        val bus = AudioBus(provider)
        val cache = BitmapCache(provider)
        val newWorld = GameWorld(project, provider, bus, cache, input, host)
        audio = bus
        bitmaps = cache
        world = newWorld
        renderer = Renderer(newWorld).also { r ->
            if (width > 0 && height > 0) r.resize(width.toFloat(), height.toFloat())
        }
        if (holder.surface?.isValid == true) startLoop()
    }

    fun restart() {
        world?.let { it.loadScene(it.sceneId) }
    }

    fun setPaused(value: Boolean) {
        paused = value
        if (value) audio?.pause() else audio?.resume()
    }

    fun release() {
        stopLoop()
        audio?.release()
        bitmaps?.clear()
        audio = null
        bitmaps = null
        world = null
        renderer = null
    }

    // ---- surface ------------------------------------------------------------------------------

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (world != null) startLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        renderer?.resize(width.toFloat(), height.toFloat())
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopLoop()
    }

    private fun startLoop() {
        if (running) return
        running = true
        loop = Thread({ runLoop() }, "blockforge-loop").also { it.start() }
    }

    private fun stopLoop() {
        running = false
        loop?.let { thread ->
            runCatching {
                thread.join(600)
                if (thread.isAlive) thread.interrupt()
            }
        }
        loop = null
    }

    private fun runLoop() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val raw = (now - last) / 1_000_000_000f
            last = now
            // Clamp so a stall (GC, app switch) never teleports actors across the scene.
            val dt = raw.coerceIn(0f, 1f / 20f)
            fps = if (raw > 0f) fps * 0.9f + (1f / raw) * 0.1f else fps

            val currentWorld = world
            val currentRenderer = renderer
            if (currentWorld != null && currentRenderer != null) {
                if (!paused) {
                    runCatching { currentWorld.update(dt) }
                        .onFailure { error -> currentWorld.host?.onError(error.message ?: "Kesalahan runtime") }
                    onFrame?.invoke(currentWorld.activeBlockIds())
                }
                drawFrame(currentWorld, currentRenderer)
            }

            val elapsed = (System.nanoTime() - now) / 1_000_000L
            val sleep = FRAME_MS - elapsed
            if (sleep > 0) {
                runCatching { Thread.sleep(sleep) }.onFailure { return }
            }
        }
    }

    private fun drawFrame(world: GameWorld, renderer: Renderer) {
        val surfaceHolder = holder
        if (!surfaceHolder.surface.isValid) return
        val canvas = runCatching { surfaceHolder.lockCanvas() }.getOrNull() ?: return
        try {
            renderer.draw(canvas, world.project.settings.showVirtualPad, fps)
        } finally {
            runCatching { surfaceHolder.unlockCanvasAndPost(canvas) }
        }
    }

    // ---- input --------------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val r = renderer ?: return false
        val showPad = world?.project?.settings?.showVirtualPad ?: false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val x = event.getX(index)
                val y = event.getY(index)
                val key = if (showPad) padKeyAt(r, x, y) else null
                if (key != null) {
                    pointerKeys[id] = key
                    input.press(key)
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                } else {
                    r.screenToWorld(x, y, worldPoint)
                    input.setPointer(worldPoint.x, worldPoint.y, true)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    if (pointerKeys.containsKey(id)) {
                        // Sliding off a d-pad button releases it and may press the neighbour.
                        val key = padKeyAt(r, event.getX(i), event.getY(i))
                        val previous = pointerKeys[id]
                        if (key != previous) {
                            previous?.let { input.release(it) }
                            if (key != null) {
                                pointerKeys[id] = key; input.press(key)
                            } else {
                                pointerKeys.remove(id)
                            }
                        }
                    } else {
                        r.screenToWorld(event.getX(i), event.getY(i), worldPoint)
                        input.setPointer(worldPoint.x, worldPoint.y, true)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                pointerKeys.remove(id)?.let { input.release(it) }
                if (event.actionMasked != MotionEvent.ACTION_POINTER_UP) {
                    input.setPointer(worldPoint.x, worldPoint.y, false)
                }
            }
        }
        return true
    }

    private fun padKeyAt(renderer: Renderer, x: Float, y: Float): String? =
        renderer.padButtons.entries.firstOrNull { it.value.contains(x, y) }?.key

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        InputState.fromKeyCode(keyCode)?.let { input.press(it); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        InputState.fromKeyCode(keyCode)?.let { input.release(it); return true }
        return super.onKeyUp(keyCode, event)
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
