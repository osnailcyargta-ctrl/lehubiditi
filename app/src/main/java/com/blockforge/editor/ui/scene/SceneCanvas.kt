package com.blockforge.editor.ui.scene

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.blockforge.editor.ui.theme.ForgeColors
import com.blockforge.engine.model.GameObject
import com.blockforge.engine.model.GameProject
import com.blockforge.engine.model.ObjectShape
import com.blockforge.engine.model.Scene
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/**
 * The scene view: a to-scale preview of the design resolution where objects are dragged into place.
 *
 * It renders with the same centre-origin convention the runtime uses, so what is arranged here is
 * exactly what the game shows on the first frame.
 */
@Composable
fun SceneCanvas(
    project: GameProject,
    scene: Scene,
    selectedId: String?,
    assetFile: (String) -> File?,
    onSelect: (String?) -> Unit,
    onMove: (id: String, x: Float, y: Float, record: Boolean) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmaps = rememberSpriteCache(project, assetFile)
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var dragId by remember { mutableStateOf<String?>(null) }

    val design = Size(project.settings.designWidth, project.settings.designHeight)

    Box(modifier.fillMaxSize().background(ForgeColors.Canvas)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(scene, zoom, pan) {
                    detectTapGestures { screen ->
                        val fit = fitOf(size.width.toFloat(), size.height.toFloat(), design, pan, zoom)
                        val world = fit.toWorld(screen)
                        onSelect(hitTest(scene, world)?.id)
                    }
                }
                .pointerInput(scene, zoom, pan) {
                    detectDragGestures(
                        onDragStart = { screen ->
                            val fit = fitOf(size.width.toFloat(), size.height.toFloat(), design, pan, zoom)
                            val world = fit.toWorld(screen)
                            val hit = hitTest(scene, world)
                            dragId = hit?.id
                            if (hit != null) {
                                onSelect(hit.id)
                                // One undo entry for the whole drag.
                                onMove(hit.id, hit.x, hit.y, true)
                            }
                        },
                        onDrag = { change, delta ->
                            val id = dragId ?: return@detectDragGestures
                            change.consume()
                            val fit = fitOf(size.width.toFloat(), size.height.toFloat(), design, pan, zoom)
                            val obj = scene.objects.firstOrNull { it.id == id } ?: return@detectDragGestures
                            onMove(id, obj.x + delta.x / fit.scale, obj.y + delta.y / fit.scale, false)
                        },
                        onDragEnd = {
                            if (dragId != null) onCommit()
                            dragId = null
                        },
                        onDragCancel = { dragId = null }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        if (dragId != null) return@detectTransformGestures
                        zoom = (zoom * zoomChange).coerceIn(0.3f, 3f)
                        pan += panChange
                    }
                }
        ) {
            val fit = fitOf(size.width, size.height, design, pan, zoom)
            translate(fit.offsetX, fit.offsetY) {
                scale(fit.scale, fit.scale, pivot = Offset.Zero) {
                    drawRect(
                        color = Color(scene.backgroundColor ?: project.settings.backgroundColor),
                        size = design
                    )
                    drawSceneGrid(design)
                    scene.objects.sortedBy { it.zIndex }.forEach { obj ->
                        drawObject(obj, bitmaps[obj.spriteAssetId], obj.id == selectedId, 1f / fit.scale)
                    }
                    // The design frame — everything outside it is off-screen at runtime.
                    drawRect(
                        color = ForgeColors.Accent.copy(alpha = 0.7f),
                        size = design,
                        style = Stroke(width = 2f / fit.scale)
                    )
                }
            }
        }
    }
}

// ---- geometry ----------------------------------------------------------------------------------

private class Fit(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun toWorld(screen: Offset) = Offset((screen.x - offsetX) / scale, (screen.y - offsetY) / scale)
}

private fun fitOf(viewW: Float, viewH: Float, design: Size, pan: Offset, zoom: Float): Fit {
    val base = min(viewW / design.width, viewH / design.height) * 0.86f
    val scale = base * zoom
    val offsetX = (viewW - design.width * scale) / 2f + pan.x
    val offsetY = (viewH - design.height * scale) / 2f + pan.y
    return Fit(scale, offsetX, offsetY)
}

private fun hitTest(scene: Scene, world: Offset): GameObject? =
    scene.objects.sortedByDescending { it.zIndex }.firstOrNull { obj ->
        val hw = obj.width * obj.scaleX / 2f
        val hh = obj.height * obj.scaleY / 2f
        abs(world.x - obj.x) <= hw && abs(world.y - obj.y) <= hh
    }

// ---- drawing -----------------------------------------------------------------------------------

private fun DrawScope.drawSceneGrid(design: Size) {
    val step = 60f
    var x = 0f
    while (x <= design.width) {
        drawLine(Color.White.copy(alpha = 0.045f), Offset(x, 0f), Offset(x, design.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= design.height) {
        drawLine(Color.White.copy(alpha = 0.045f), Offset(0f, y), Offset(design.width, y), strokeWidth = 1f)
        y += step
    }
}

private fun DrawScope.drawObject(
    obj: GameObject,
    sprite: ImageBitmap?,
    selected: Boolean,
    inverseScale: Float
) {
    val w = obj.width * obj.scaleX
    val h = obj.height * obj.scaleY
    if (!obj.visible) return

    translate(obj.x, obj.y) {
        rotate(obj.rotation, pivot = Offset.Zero) {
            val topLeft = Offset(-w / 2f, -h / 2f)
            if (sprite != null) {
                drawImage(
                    image = sprite,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(sprite.width, sprite.height),
                    dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                    dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
                    alpha = obj.alpha
                )
            } else if (obj.shape == ObjectShape.CIRCLE) {
                drawOval(Color(obj.fallbackColor).copy(alpha = obj.alpha), topLeft, Size(w, h))
            } else {
                drawRoundRect(
                    color = Color(obj.fallbackColor).copy(alpha = obj.alpha),
                    topLeft = topLeft,
                    size = Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f)
                )
            }
            if (obj.physics.enabled) {
                drawRect(
                    color = if (obj.physics.static) ForgeColors.Success else ForgeColors.Running,
                    topLeft = topLeft,
                    size = Size(w, h),
                    style = Stroke(width = 1.5f * inverseScale, pathEffect = dash(inverseScale))
                )
            }
            if (selected) {
                val pad = 6f * inverseScale
                drawRect(
                    color = Color.White,
                    topLeft = Offset(topLeft.x - pad, topLeft.y - pad),
                    size = Size(w + pad * 2, h + pad * 2),
                    style = Stroke(width = 2f * inverseScale)
                )
                listOf(
                    Offset(topLeft.x - pad, topLeft.y - pad),
                    Offset(topLeft.x + w + pad, topLeft.y - pad),
                    Offset(topLeft.x - pad, topLeft.y + h + pad),
                    Offset(topLeft.x + w + pad, topLeft.y + h + pad)
                ).forEach { corner ->
                    drawCircle(ForgeColors.Accent, radius = 5f * inverseScale, center = corner)
                }
            }
        }
    }
}

private fun dash(inverse: Float) =
    androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f * inverse, 6f * inverse), 0f)

// ---- sprite cache ------------------------------------------------------------------------------

/** Decodes project sprites once per asset list and releases them when the screen goes away. */
@Composable
private fun rememberSpriteCache(
    project: GameProject,
    assetFile: (String) -> File?
): Map<String?, ImageBitmap?> {
    val cache = remember(project.assets) {
        project.assets
            .filter { it.kind == com.blockforge.engine.model.AssetKind.IMAGE }
            .associate { ref ->
                val bitmap = assetFile(ref.fileName)
                    ?.takeIf { it.isFile }
                    ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
                ref.id as String? to bitmap?.asImageBitmap()
            }
    }
    DisposableEffect(cache) { onDispose { } }
    return cache
}
