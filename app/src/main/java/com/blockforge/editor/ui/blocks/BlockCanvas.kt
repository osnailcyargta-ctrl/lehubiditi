package com.blockforge.editor.ui.blocks

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blockforge.editor.InsertionTarget
import com.blockforge.editor.ui.theme.ForgeColors
import com.blockforge.engine.blocks.BlockShape
import com.blockforge.engine.blocks.SlotDef
import com.blockforge.engine.model.BlockNode
import kotlin.math.abs

/**
 * The script canvas.
 *
 * Everything is drawn by hand rather than composed from widgets, because the layout is a tree of
 * lanes with elbow connectors between them — geometry that is far easier to own outright than to
 * coax out of nested layouts, and it keeps a 200-block script at a steady frame rate.
 */
@Composable
fun BlockCanvas(
    scripts: List<BlockNode>,
    activeBlocks: Set<String>,
    selectedId: String?,
    insertion: InsertionTarget?,
    slotLabel: (SlotDef, String) -> String,
    onSelectBlock: (String?) -> Unit,
    onSelectGap: (String, Int) -> Unit,
    onSlotTap: (String, String) -> Unit,
    onMoveScript: (id: String, x: Float, y: Float, record: Boolean) -> Unit,
    onMoveBlock: (blockId: String, laneId: String, index: Int) -> Unit,
    onCommitEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 512)

    val widthCache = remember { HashMap<Long, Float>() }
    val measureWidth: (String, Float) -> Float = remember(measurer) {
        { text, sizeSp ->
            val key = text.hashCode().toLong() * 31 + sizeSp.toInt()
            widthCache.getOrPut(key) {
                measurer.measure(AnnotatedString(text), TextStyle(fontSize = sizeSp.sp)).size.width.toFloat()
            }
        }
    }

    val metrics = remember(density, slotLabel) {
        with(density) {
            BlockLayoutEngine.Metrics(
                blockHeight = 56.dp.toPx(),
                laneGapY = 12.dp.toPx(),
                branchIndentX = 34.dp.toPx(),
                branchGapY = 10.dp.toPx(),
                blockPadX = 16.dp.toPx(),
                partGap = 7.dp.toPx(),
                chipPadX = 10.dp.toPx(),
                minBlockWidth = 130.dp.toPx(),
                emptyLaneHeight = 40.dp.toPx(),
                addButtonWidth = 96.dp.toPx(),
                gapHitHeight = 22.dp.toPx(),
                scriptSpacing = 48.dp.toPx(),
                textWidth = measureWidth,
                slotLabel = slotLabel
            )
        }
    }

    val layout = remember(scripts, metrics) { BlockLayoutEngine.layout(scripts, metrics) }

    var pan by remember { mutableStateOf(Offset(60f, 60f)) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var dragBlockId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragIsScript by remember { mutableStateOf(false) }
    var hoverGap by remember { mutableStateOf<LaidGap?>(null) }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    fun toCanvas(point: Offset) = (point - pan) / zoom

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ForgeColors.Canvas)
            .pointerInput(layout) {
                detectTapGestures { screen ->
                    val p = toCanvas(screen)
                    val slot = layout.slotAt(p.x, p.y)
                    if (slot != null) {
                        onSlotTap(slot.blockId, slot.slot.key)
                        return@detectTapGestures
                    }
                    val gap = layout.gaps.firstOrNull { gapHit(it, p, metrics.gapHitHeight) }
                    if (gap != null) {
                        onSelectGap(gap.laneId, gap.index)
                        return@detectTapGestures
                    }
                    onSelectBlock(layout.blockAt(p.x, p.y)?.node?.id)
                }
            }
            .pointerInput(layout) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { screen ->
                        val p = toCanvas(screen)
                        val block = layout.blockAt(p.x, p.y)
                        if (block != null) {
                            dragBlockId = block.node.id
                            dragIsScript = block.def.shape == BlockShape.HAT
                            dragOffset = Offset.Zero
                            onSelectBlock(block.node.id)
                            // Snapshot the pre-drag position once so the whole drag is one undo step.
                            if (dragIsScript) {
                                onMoveScript(block.node.id, block.node.canvasX, block.node.canvasY, true)
                            }
                        }
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        val id = dragBlockId ?: return@detectDragGesturesAfterLongPress
                        val block = layout.block(id) ?: return@detectDragGesturesAfterLongPress
                        val step = delta / zoom
                        if (dragIsScript) {
                            // The model moves directly, so no extra visual offset is applied.
                            onMoveScript(id, block.node.canvasX + step.x, block.node.canvasY + step.y, false)
                        } else {
                            dragOffset += step
                            val tip = Offset(block.x + dragOffset.x, block.y + dragOffset.y)
                            hoverGap = nearestGap(layout, tip, id, metrics.blockHeight * 2.2f)
                        }
                    },
                    onDragEnd = {
                        val id = dragBlockId
                        val target = hoverGap
                        if (id != null && !dragIsScript && target != null) {
                            onMoveBlock(id, target.laneId, target.index)
                        }
                        onCommitEdit()
                        dragBlockId = null
                        hoverGap = null
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = {
                        dragBlockId = null
                        hoverGap = null
                        dragOffset = Offset.Zero
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    if (dragBlockId != null) return@detectTransformGestures
                    zoom = (zoom * zoomChange).coerceIn(0.35f, 2.2f)
                    pan += panChange
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawGrid(pan, zoom)
            translate(pan.x, pan.y) {
                scaleAround(zoom) {
                    drawLaneBackdrops(layout)
                    drawConnectors(layout)
                    drawGaps(layout, insertion, hoverGap, pulse, measurer)
                    layout.blocks.forEach { block ->
                        val dragging = block.node.id == dragBlockId
                        val shift = if (dragging && !dragIsScript) dragOffset else Offset.Zero
                        translate(shift.x, shift.y) {
                            drawBlock(
                                block = block,
                                measurer = measurer,
                                selected = block.node.id == selectedId,
                                running = block.node.id in activeBlocks,
                                dragging = dragging,
                                pulse = pulse
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- gesture helpers ---------------------------------------------------------------------------

private fun gapHit(gap: LaidGap, point: Offset, slack: Float): Boolean =
    point.x >= gap.x - slack && point.x <= gap.x + gap.width + slack &&
        abs(point.y - gap.y) <= slack

/** Finds the insertion point a dragged block would snap into, skipping the block's own lanes. */
private fun nearestGap(layout: ScriptLayout, tip: Offset, draggedId: String, maxDistance: Float): LaidGap? {
    val dragged = layout.block(draggedId) ?: return null
    val forbidden = forbiddenLanes(layout, dragged)
    return layout.gaps
        .filterNot { it.laneId in forbidden }
        .minByOrNull { gap ->
            val dx = tip.x - gap.x
            val dy = tip.y - gap.y
            dx * dx + dy * dy
        }
        ?.takeIf { gap ->
            val dx = tip.x - gap.x
            val dy = tip.y - gap.y
            kotlin.math.sqrt(dx * dx + dy * dy) <= maxDistance
        }
}

/** A block can never be dropped into a lane it owns — that would make the tree eat itself. */
private fun forbiddenLanes(layout: ScriptLayout, dragged: LaidBlock): Set<String> {
    val result = HashSet<String>()
    fun walk(node: BlockNode) {
        node.branches.forEach { lane ->
            result.add(lane.id)
            lane.nodes.forEach { walk(it) }
        }
    }
    walk(dragged.node)
    return result
}

// ---- drawing -----------------------------------------------------------------------------------

private inline fun DrawScope.scaleAround(factor: Float, crossinline block: DrawScope.() -> Unit) {
    if (factor == 1f) {
        block(); return
    }
    scale(factor, factor, pivot = Offset.Zero) { block() }
}

private fun DrawScope.drawGrid(pan: Offset, zoom: Float) {
    val step = 32.dp.toPx() * zoom
    if (step < 8f) return
    val color = ForgeColors.CanvasGrid
    var x = pan.x % step
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = pan.y % step
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

/**
 * A faint rounded slab behind every lane. This is what makes "one lane down, branches to the right"
 * legible at a glance — you can see the shape of the program before reading a single word.
 */
private fun DrawScope.drawLaneBackdrops(layout: ScriptLayout) {
    layout.lanes.forEach { lane ->
        val pad = 10.dp.toPx()
        val width = maxOf(lane.width, 96.dp.toPx()) + pad * 2
        drawRoundRect(
            color = ForgeColors.PanelRaised.copy(alpha = 0.5f),
            topLeft = Offset(lane.x - pad, lane.y - pad),
            size = Size(width, lane.height + pad * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
        )
        drawRoundRect(
            color = ForgeColors.Outline.copy(alpha = 0.55f),
            topLeft = Offset(lane.x - pad, lane.y - pad),
            size = Size(width, lane.height + pad * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

private fun DrawScope.drawConnectors(layout: ScriptLayout) {
    layout.connectors.forEach { c ->
        val color = Color(c.color).copy(alpha = 0.75f)
        val stroke = Stroke(width = 3.dp.toPx())
        if (!c.elbow) {
            drawLine(color, Offset(c.fromX, c.fromY), Offset(c.toX, c.toY), strokeWidth = stroke.width)
            return@forEach
        }
        val midX = c.fromX + (c.toX - c.fromX) * 0.55f
        val radius = 10.dp.toPx()
        val path = Path().apply {
            moveTo(c.fromX, c.fromY)
            lineTo(midX - radius, c.fromY)
            quadraticBezierTo(midX, c.fromY, midX, c.fromY + radius * dirSign(c.toY - c.fromY))
            lineTo(midX, c.toY - radius * dirSign(c.toY - c.fromY))
            quadraticBezierTo(midX, c.toY, midX + radius, c.toY)
            lineTo(c.toX, c.toY)
        }
        drawPath(path, color, style = stroke)
        // Arrow head so the direction of flow into the branch is unmistakable.
        drawCircle(color, radius = 4.dp.toPx(), center = Offset(c.toX, c.toY))
    }
}

private fun dirSign(delta: Float): Float = if (delta >= 0f) 1f else -1f

private fun DrawScope.drawGaps(
    layout: ScriptLayout,
    insertion: InsertionTarget?,
    hover: LaidGap?,
    pulse: Float,
    measurer: androidx.compose.ui.text.TextMeasurer
) {
    layout.gaps.forEach { gap ->
        val isInsertion = insertion != null && insertion.laneId == gap.laneId && insertion.index == gap.index
        val isHover = hover != null && hover.laneId == gap.laneId && hover.index == gap.index

        if (gap.isTail) {
            val h = 30.dp.toPx()
            val active = isInsertion || isHover
            drawRoundRect(
                color = if (active) ForgeColors.Accent.copy(alpha = 0.9f * pulse + 0.1f)
                else ForgeColors.PanelRaised.copy(alpha = 0.9f),
                topLeft = Offset(gap.x, gap.y),
                size = Size(gap.width, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(15.dp.toPx())
            )
            drawRoundRect(
                color = if (active) ForgeColors.Accent else ForgeColors.Outline,
                topLeft = Offset(gap.x, gap.y),
                size = Size(gap.width, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(15.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = if (active) null else dashed())
            )
            drawCanvasText(
                measurer, "+ blok", gap.x + gap.width / 2f, gap.y + h / 2f,
                12f, if (active) Color(0xFF04121A) else ForgeColors.TextMuted, FontWeight.SemiBold, centered = true
            )
        } else if (isInsertion || isHover) {
            drawRoundRect(
                color = ForgeColors.Accent.copy(alpha = pulse),
                topLeft = Offset(gap.x, gap.y - 3.dp.toPx()),
                size = Size(maxOf(gap.width, 120.dp.toPx()), 6.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            )
        }
    }
}

private fun dashed() = PathEffect.dashPathEffect(floatArrayOf(9f, 8f), 0f)

private fun DrawScope.drawBlock(
    block: LaidBlock,
    measurer: androidx.compose.ui.text.TextMeasurer,
    selected: Boolean,
    running: Boolean,
    dragging: Boolean,
    pulse: Float
) {
    val base = Color(block.def.category.color)
    val radius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
    val topLeft = Offset(block.x, block.y)
    val size = Size(block.width, block.height)

    // Running glow: three fading rings, cheap and legible on a dark canvas.
    if (running) {
        repeat(3) { i ->
            val spread = (4 + i * 5).dp.toPx()
            drawRoundRect(
                color = ForgeColors.Running.copy(alpha = 0.28f * pulse / (i + 1)),
                topLeft = Offset(block.x - spread, block.y - spread),
                size = Size(block.width + spread * 2, block.height + spread * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx() + spread)
            )
        }
    }

    if (dragging) {
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.45f),
            topLeft = Offset(block.x + 6.dp.toPx(), block.y + 10.dp.toPx()),
            size = size,
            cornerRadius = radius
        )
    }

    drawRoundRect(color = base.copy(alpha = if (dragging) 0.92f else 1f), topLeft = topLeft, size = size, cornerRadius = radius)
    // A darker foot gives the block a lit-from-above feel without a real gradient shader.
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.18f),
        topLeft = Offset(block.x, block.y + block.height * 0.62f),
        size = Size(block.width, block.height * 0.38f),
        cornerRadius = radius
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.16f),
        topLeft = topLeft,
        size = Size(block.width, block.height * 0.42f),
        cornerRadius = radius
    )

    // Shape cue on the left edge: hats get a cap, terminals get a stop bar.
    when (block.def.shape) {
        BlockShape.HAT -> drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(block.x + 8.dp.toPx(), block.y + 10.dp.toPx()),
            size = Size(5.dp.toPx(), block.height - 20.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
        )

        BlockShape.TERMINAL -> drawRoundRect(
            color = Color.Black.copy(alpha = 0.45f),
            topLeft = Offset(block.x + 8.dp.toPx(), block.y + block.height / 2f - 2.dp.toPx()),
            size = Size(5.dp.toPx(), 4.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        )

        else -> Unit
    }

    drawParts(block.parts, measurer, Color.White)

    if (selected) {
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(block.x - 2.dp.toPx(), block.y - 2.dp.toPx()),
            size = Size(block.width + 4.dp.toPx(), block.height + 4.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
}

private fun DrawScope.drawParts(
    parts: List<LaidPart>,
    measurer: androidx.compose.ui.text.TextMeasurer,
    textColor: Color
) {
    parts.forEach { part ->
        when (part) {
            is LaidPart.Text -> drawCanvasText(
                measurer, part.text, part.x, part.y, part.sizeSp, textColor, FontWeight.SemiBold
            )

            is LaidPart.Chip -> {
                val slot = part.slot
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.34f),
                    topLeft = Offset(slot.x, slot.y),
                    size = Size(slot.width, slot.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(slot.height / 2f)
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.22f),
                    topLeft = Offset(slot.x, slot.y),
                    size = Size(slot.width, slot.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(slot.height / 2f),
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCanvasText(
                    measurer,
                    slot.display,
                    slot.x + slot.width / 2f,
                    slot.y + slot.height / 2f,
                    (14f - slot.depth).coerceAtLeast(11f),
                    Color.White,
                    FontWeight.Medium,
                    centered = true
                )
            }

            is LaidPart.Nested -> {
                val corner = androidx.compose.ui.geometry.CornerRadius(part.height / 2f)
                drawRoundRect(
                    color = Color(part.def.category.color),
                    topLeft = Offset(part.x, part.y),
                    size = Size(part.width, part.height),
                    cornerRadius = corner
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.28f),
                    topLeft = Offset(part.x, part.y),
                    size = Size(part.width, part.height),
                    cornerRadius = corner,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawParts(part.parts, measurer, Color.White)
            }
        }
    }
}

private fun DrawScope.drawCanvasText(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    x: Float,
    centerY: Float,
    sizeSp: Float,
    color: Color,
    weight: FontWeight,
    centered: Boolean = false
) {
    if (text.isEmpty()) return
    val style = TextStyle(fontSize = sizeSp.sp, color = color, fontWeight = weight)
    val result = measurer.measure(AnnotatedString(text), style, maxLines = 1)
    val left = if (centered) x - result.size.width / 2f else x
    drawText(result, topLeft = Offset(left, centerY - result.size.height / 2f))
}

/** Bounding box helper used by "pusatkan tampilan" in the toolbar. */
fun ScriptLayout.contentBounds(): Rect =
    if (blocks.isEmpty()) Rect(0f, 0f, 0f, 0f) else Rect(minX, minY, minX + width, minY + height)
