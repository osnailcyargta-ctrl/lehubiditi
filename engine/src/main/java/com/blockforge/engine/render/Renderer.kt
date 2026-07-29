package com.blockforge.engine.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.blockforge.engine.model.ObjectShape
import com.blockforge.engine.runtime.Actor
import com.blockforge.engine.runtime.GameWorld
import kotlin.math.min

/**
 * Draws a [GameWorld] into a [Canvas].
 *
 * The game is authored against a fixed virtual resolution and letterboxed into whatever the device
 * gives us, so a layout that looks right in the editor looks right on every phone.
 */
class Renderer(private val world: GameWorld) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val srcRect = Rect()
    private val dstRect = RectF()
    private val bubble = RectF()

    var scale = 1f
        private set
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    private var viewW = 0f
    private var viewH = 0f

    /** Screen-space rectangles for the virtual gamepad, rebuilt whenever the surface resizes. */
    val padButtons = LinkedHashMap<String, RectF>()

    fun resize(width: Float, height: Float) {
        viewW = width
        viewH = height
        val dw = world.viewWidth
        val dh = world.viewHeight
        scale = min(width / dw, height / dh)
        offsetX = (width - dw * scale) / 2f
        offsetY = (height - dh * scale) / 2f
        layoutPad(width, height)
    }

    fun screenToWorld(x: Float, y: Float, out: PointF = PointF()): PointF {
        val wx = (x - offsetX) / scale + (world.cameraX - world.viewWidth / 2f)
        val wy = (y - offsetY) / scale + (world.cameraY - world.viewHeight / 2f)
        out.set(wx, wy)
        return out
    }

    fun draw(canvas: Canvas, showPad: Boolean, fps: Float = 0f) {
        val settings = world.project.settings
        val scene = world.project.scene(world.sceneId)
        canvas.drawColor(0xFF05070A.toInt())

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, world.viewWidth, world.viewHeight)

        fill.style = Paint.Style.FILL
        fill.color = scene?.backgroundColor ?: settings.backgroundColor
        canvas.drawRect(0f, 0f, world.viewWidth, world.viewHeight, fill)

        canvas.save()
        canvas.translate(
            -(world.cameraX - world.viewWidth / 2f) + world.shakeOffsetX,
            -(world.cameraY - world.viewHeight / 2f) + world.shakeOffsetY
        )

        scene?.backgroundAssetId?.let { id ->
            world.bitmaps.get(world.fileNameOf(id))?.let { bmp ->
                srcRect.set(0, 0, bmp.width, bmp.height)
                dstRect.set(0f, 0f, world.viewWidth, world.viewHeight)
                canvas.drawBitmap(bmp, srcRect, dstRect, spritePaint)
            }
        }

        world.actors.sortedBy { it.zIndex }.forEach { actor -> drawActor(canvas, actor) }
        world.actors.forEach { actor -> drawSpeech(canvas, actor) }

        canvas.restore()
        drawWatchers(canvas)
        canvas.restore()

        if (showPad) drawPad(canvas)
        if (settings.showFps && fps > 0f) {
            text.color = 0xB0FFFFFF.toInt()
            text.textSize = 28f
            canvas.drawText("${fps.toInt()} fps", 18f, 40f, text)
        }
    }

    private fun drawActor(canvas: Canvas, actor: Actor) {
        if (!actor.visible || actor.alpha <= 0.01f) return
        val w = actor.drawWidth
        val h = actor.drawHeight
        if (w <= 0f || h <= 0f) return

        canvas.save()
        canvas.translate(actor.x, actor.y)
        if (actor.rotation != 0f) canvas.rotate(actor.rotation)

        val bitmap = world.bitmaps.get(actor.spriteFile)
        if (bitmap != null) {
            spritePaint.alpha = (actor.alpha * 255).toInt().coerceIn(0, 255)
            srcRect.set(0, 0, bitmap.width, bitmap.height)
            dstRect.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
            canvas.drawBitmap(bitmap, srcRect, dstRect, spritePaint)
        } else {
            fill.style = Paint.Style.FILL
            fill.color = actor.color
            fill.alpha = (actor.alpha * 255).toInt().coerceIn(0, 255)
            dstRect.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
            if (actor.shape == ObjectShape.CIRCLE) {
                canvas.drawOval(dstRect, fill)
            } else {
                canvas.drawRoundRect(dstRect, w * 0.08f, h * 0.08f, fill)
            }
            // A hairline keeps same-coloured objects readable when they overlap.
            stroke.color = darken(actor.color)
            stroke.alpha = (actor.alpha * 160).toInt().coerceIn(0, 255)
            stroke.strokeWidth = 2f
            if (actor.shape == ObjectShape.CIRCLE) canvas.drawOval(dstRect, stroke)
            else canvas.drawRoundRect(dstRect, w * 0.08f, h * 0.08f, stroke)
        }
        canvas.restore()
    }

    private fun drawSpeech(canvas: Canvas, actor: Actor) {
        val say = actor.sayText ?: return
        if (say.isEmpty()) return
        text.textSize = 22f
        val tw = text.measureText(say)
        val padX = 14f
        val padY = 10f
        val bx = actor.x
        val by = actor.y - actor.drawHeight / 2f - 18f
        bubble.set(bx - tw / 2f - padX, by - 22f - padY, bx + tw / 2f + padX, by + padY)

        fill.style = Paint.Style.FILL
        fill.color = 0xF2FFFFFF.toInt()
        canvas.drawRoundRect(bubble, 12f, 12f, fill)
        fill.color = 0xFF1A1F29.toInt()
        canvas.drawRect(bx - 6f, bubble.bottom - 1f, bx + 6f, bubble.bottom + 8f, fill)

        text.color = 0xFF11151C.toInt()
        canvas.drawText(say, bx - tw / 2f, bubble.bottom - padY - 4f, text)
    }

    private fun drawWatchers(canvas: Canvas) {
        val watched = world.watchedVariables()
        if (watched.isEmpty()) return
        text.textSize = 24f
        var y = 24f
        watched.forEach { (name, value) ->
            val label = "$name: $value"
            val tw = text.measureText(label)
            bubble.set(16f, y, 16f + tw + 28f, y + 36f)
            fill.style = Paint.Style.FILL
            fill.color = 0xCC121820.toInt()
            canvas.drawRoundRect(bubble, 10f, 10f, fill)
            fill.color = 0xFFFF7043.toInt()
            canvas.drawRoundRect(RectF(16f, y, 22f, y + 36f), 3f, 3f, fill)
            text.color = Color.WHITE
            canvas.drawText(label, 30f, y + 25f, text)
            y += 44f
        }
    }

    // ---- virtual gamepad ----------------------------------------------------------------------

    private fun layoutPad(width: Float, height: Float) {
        padButtons.clear()
        val unit = min(width, height) * 0.13f
        val margin = unit * 0.55f
        val padCx = margin + unit * 1.5f
        val padCy = height - margin - unit * 1.5f

        fun square(cx: Float, cy: Float, size: Float) =
            RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)

        padButtons["LEFT"] = square(padCx - unit, padCy, unit)
        padButtons["RIGHT"] = square(padCx + unit, padCy, unit)
        padButtons["UP"] = square(padCx, padCy - unit, unit)
        padButtons["DOWN"] = square(padCx, padCy + unit, unit)

        val actionCx = width - margin - unit
        val actionCy = height - margin - unit
        padButtons["A"] = square(actionCx, actionCy, unit * 1.15f)
        padButtons["B"] = square(actionCx - unit * 1.4f, actionCy - unit * 0.5f, unit)
        padButtons["SPACE"] = square(actionCx - unit * 0.7f, actionCy - unit * 1.6f, unit * 0.9f)
    }

    private fun drawPad(canvas: Canvas) {
        padButtons.forEach { (key, rect) ->
            val held = world.input.isHeld(key)
            fill.style = Paint.Style.FILL
            fill.color = if (held) 0x66FFFFFF else 0x2EFFFFFF
            canvas.drawRoundRect(rect, rect.width() * 0.28f, rect.width() * 0.28f, fill)

            stroke.color = if (held) 0xCCFFFFFF.toInt() else 0x55FFFFFF
            stroke.strokeWidth = 2.5f
            canvas.drawRoundRect(rect, rect.width() * 0.28f, rect.width() * 0.28f, stroke)

            text.color = if (held) 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt()
            text.textSize = rect.width() * 0.42f
            val glyph = padGlyph(key)
            val tw = text.measureText(glyph)
            canvas.drawText(glyph, rect.centerX() - tw / 2f, rect.centerY() + text.textSize * 0.35f, text)
        }
    }

    private fun padGlyph(key: String) = when (key) {
        "LEFT" -> "◀"
        "RIGHT" -> "▶"
        "UP" -> "▲"
        "DOWN" -> "▼"
        "SPACE" -> "␣"
        else -> key
    }

    private fun darken(color: Int): Int {
        val r = (Color.red(color) * 0.6f).toInt()
        val g = (Color.green(color) * 0.6f).toInt()
        val b = (Color.blue(color) * 0.6f).toInt()
        return Color.argb(255, r, g, b)
    }
}
