package com.blockforge.editor.ui.blocks

import com.blockforge.engine.blocks.BlockCatalog
import com.blockforge.engine.blocks.BlockDef
import com.blockforge.engine.blocks.BlockShape
import com.blockforge.engine.blocks.BranchDirection
import com.blockforge.engine.blocks.LabelPart
import com.blockforge.engine.blocks.SlotDef
import com.blockforge.engine.model.Arg
import com.blockforge.engine.model.BlockNode
import com.blockforge.engine.model.Lane

/**
 * Turns a script forest into absolute pixel geometry.
 *
 * The shape of the result is the whole point of the editor: a hat owns one lane that runs straight
 * down, and any block with a body opens a *new lane to the right*, joined by an elbow. Nothing here
 * stores a size — lanes are measured from their contents, so adding a block lengthens the lane and
 * deleting one shortens it without any separate bookkeeping.
 */
object BlockLayoutEngine {

    fun layout(scripts: List<BlockNode>, m: Metrics): ScriptLayout {
        val builder = Builder(m)
        scripts.forEach { hat -> builder.placeScript(hat) }
        return builder.finish()
    }

    /** Everything the layout needs from the theme and the text measurer, in pixels. */
    class Metrics(
        val blockHeight: Float,
        val laneGapY: Float,
        val branchIndentX: Float,
        val branchGapY: Float,
        val blockPadX: Float,
        val partGap: Float,
        val chipPadX: Float,
        val minBlockWidth: Float,
        val emptyLaneHeight: Float,
        val addButtonWidth: Float,
        val gapHitHeight: Float,
        val scriptSpacing: Float,
        /** Width of `text` rendered at `sizeSp`. Backed by a cached TextMeasurer. */
        val textWidth: (String, Float) -> Float,
        /** Turns a raw slot value into what the chip shows — ids become variable/asset/object names. */
        val slotLabel: (SlotDef, String) -> String
    ) {
        fun fontSize(depth: Int): Float = (14f - depth).coerceAtLeast(11f)
        fun chipHeight(depth: Int): Float = (30f - depth * 4f).coerceAtLeast(20f) * chipScale
        var chipScale: Float = 1f
    }
}

// ---- result types ------------------------------------------------------------------------------

data class ScriptLayout(
    val blocks: List<LaidBlock>,
    val lanes: List<LaidLane>,
    val gaps: List<LaidGap>,
    val slots: List<LaidSlot>,
    val connectors: List<Connector>,
    val width: Float,
    val height: Float,
    val minX: Float,
    val minY: Float
) {
    fun block(id: String): LaidBlock? = blocks.firstOrNull { it.node.id == id }

    /** Block under the point. Lanes never overlap, so at most one block can match. */
    fun blockAt(x: Float, y: Float): LaidBlock? =
        blocks.lastOrNull { x >= it.x && x <= it.x + it.width && y >= it.y && y <= it.y + it.height }

    fun slotAt(x: Float, y: Float): LaidSlot? =
        slots.lastOrNull { x >= it.x && x <= it.x + it.width && y >= it.y && y <= it.y + it.height }

    fun gapAt(x: Float, y: Float, slack: Float): LaidGap? =
        gaps.minByOrNull { gap ->
            val dx = (x - (gap.x + gap.width / 2f))
            val dy = (y - gap.y)
            dx * dx + dy * dy
        }?.takeIf { gap ->
            x >= gap.x - slack && x <= gap.x + gap.width + slack &&
                y >= gap.y - slack && y <= gap.y + slack
        }
}

data class LaidBlock(
    val node: BlockNode,
    val def: BlockDef,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    /** Height of this block plus every lane hanging off it — used to drag a whole subtree. */
    val subtreeHeight: Float,
    val subtreeWidth: Float,
    val depth: Int,
    val parts: List<LaidPart>
)

data class LaidLane(
    val lane: Lane,
    val ownerId: String?,
    val branchIndex: Int,
    val direction: BranchDirection,
    val label: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

/** An insertion point in a lane. Rendered as a thin bar, or a "+" pill at the end of the lane. */
data class LaidGap(
    val laneId: String,
    val index: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val isTail: Boolean
)

data class LaidSlot(
    val blockId: String,
    val slot: SlotDef,
    val display: String,
    val nested: BlockNode?,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val depth: Int,
    val color: Int?
)

sealed interface LaidPart {
    data class Text(val text: String, val x: Float, val y: Float, val sizeSp: Float) : LaidPart
    data class Chip(val slot: LaidSlot) : LaidPart
    data class Nested(
        val node: BlockNode,
        val def: BlockDef,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val depth: Int,
        val parts: List<LaidPart>
    ) : LaidPart
}

data class Connector(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val elbow: Boolean,
    val color: Int
)

// ---- builder -----------------------------------------------------------------------------------

private class Builder(private val m: BlockLayoutEngine.Metrics) {

    private val blocks = mutableListOf<LaidBlock>()
    private val lanes = mutableListOf<LaidLane>()
    private val gaps = mutableListOf<LaidGap>()
    private val slots = mutableListOf<LaidSlot>()
    private val connectors = mutableListOf<Connector>()

    private var minX = Float.MAX_VALUE
    private var minY = Float.MAX_VALUE
    private var maxX = -Float.MAX_VALUE
    private var maxY = -Float.MAX_VALUE

    fun placeScript(hat: BlockNode) {
        placeBlock(hat, hat.canvasX, hat.canvasY, depth = 0)
    }

    fun finish(): ScriptLayout {
        if (blocks.isEmpty()) return ScriptLayout(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0f, 0f, 0f, 0f)
        return ScriptLayout(
            blocks = blocks.toList(),
            lanes = lanes.toList(),
            gaps = gaps.toList(),
            slots = slots.toList(),
            connectors = connectors.toList(),
            width = maxX - minX,
            height = maxY - minY,
            minX = minX,
            minY = minY
        )
    }

    private fun expand(x: Float, y: Float, w: Float, h: Float) {
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x + w > maxX) maxX = x + w
        if (y + h > maxY) maxY = y + h
    }

    /** Places a block and everything hanging off it. Returns the total space it consumed. */
    private fun placeBlock(node: BlockNode, x: Float, y: Float, depth: Int): Size {
        val def = BlockCatalog[node.type] ?: unknownDef(node.type)
        val parts = layoutParts(node, def, x + m.blockPadX, y, m.blockHeight, level = 0)
        val contentWidth = parts.totalWidth
        val width = (contentWidth + m.blockPadX * 2).coerceAtLeast(m.minBlockWidth)
        val height = m.blockHeight

        expand(x, y, width, height)

        var subtreeBottom = y + height
        var subtreeRight = x + width

        if (def.branchDirection == BranchDirection.DOWN) {
            // Hat block: the main lane continues straight down, directly beneath it.
            val lane = node.branch(0)
            if (lane != null) {
                val laneY = y + height + m.laneGapY
                val size = placeLane(lane, node.id, 0, BranchDirection.DOWN, x, laneY, def.color())
                subtreeBottom = laneY + size.height
                subtreeRight = maxOf(subtreeRight, x + size.width)
                connectors += Connector(x + INSET, y + height, x + INSET, laneY, elbow = false, color = def.color())
            }
        } else {
            // Everything else: each body opens a new lane to the right of this block.
            var branchY = y
            node.branches.forEachIndexed { index, lane ->
                val laneX = x + width + m.branchIndentX
                val size = placeLane(lane, node.id, index, BranchDirection.RIGHT, laneX, branchY, def.color())
                connectors += Connector(
                    fromX = x + width,
                    fromY = y + height / 2f,
                    toX = laneX,
                    toY = branchY + m.blockHeight / 2f,
                    elbow = true,
                    color = def.color()
                )
                branchY += maxOf(size.height, m.emptyLaneHeight) + m.branchGapY
                subtreeRight = maxOf(subtreeRight, laneX + size.width)
            }
            if (node.branches.isNotEmpty()) {
                subtreeBottom = maxOf(y + height, branchY - m.branchGapY)
            }
        }

        blocks += LaidBlock(
            node = node,
            def = def,
            x = x,
            y = y,
            width = width,
            height = height,
            subtreeHeight = subtreeBottom - y,
            subtreeWidth = subtreeRight - x,
            depth = depth,
            parts = parts.parts
        )

        return Size(subtreeRight - x, subtreeBottom - y)
    }

    private fun placeLane(
        lane: Lane,
        ownerId: String?,
        branchIndex: Int,
        direction: BranchDirection,
        x: Float,
        y: Float,
        color: Int
    ): Size {
        var cursorY = y
        var widest = m.minBlockWidth

        lane.nodes.forEachIndexed { index, child ->
            gaps += LaidGap(lane.id, index, x, cursorY - m.laneGapY / 2f, m.minBlockWidth, isTail = false)
            val size = placeBlock(child, x, cursorY, depth = branchIndex + 1)
            if (index > 0) {
                connectors += Connector(x + INSET, cursorY - m.laneGapY, x + INSET, cursorY, elbow = false, color = color)
            }
            widest = maxOf(widest, size.width)
            cursorY += size.height + m.laneGapY
        }

        // Tail gap doubles as the "+" button that grows the lane by one row.
        gaps += LaidGap(lane.id, lane.nodes.size, x, cursorY, m.addButtonWidth, isTail = true)

        val height = if (lane.nodes.isEmpty()) m.emptyLaneHeight else (cursorY - y - m.laneGapY) + m.emptyLaneHeight
        lanes += LaidLane(
            lane = lane,
            ownerId = ownerId,
            branchIndex = branchIndex,
            direction = direction,
            label = lane.label,
            x = x,
            y = y,
            width = widest,
            height = height
        )
        expand(x, y, maxOf(widest, m.addButtonWidth), height)
        return Size(maxOf(widest, m.addButtonWidth), height)
    }

    // ---- label / slot layout ------------------------------------------------------------------

    private class Parts(val parts: List<LaidPart>, val totalWidth: Float)

    /**
     * Lays a block's label out horizontally: static words, value chips, and — for slots holding
     * another block — that block drawn inline at a smaller size, up to [MAX_INLINE_DEPTH].
     */
    private fun layoutParts(
        node: BlockNode,
        def: BlockDef,
        startX: Float,
        blockTop: Float,
        blockHeight: Float,
        level: Int
    ): Parts {
        val out = mutableListOf<LaidPart>()
        var cursor = startX
        val fontSize = m.fontSize(level)
        val chipH = m.chipHeight(level)

        def.parts.forEachIndexed { index, part ->
            if (index > 0) cursor += m.partGap
            when (part) {
                is LabelPart.Text -> {
                    val w = m.textWidth(part.text, fontSize)
                    out += LaidPart.Text(part.text, cursor, blockTop + blockHeight / 2f, fontSize)
                    cursor += w
                }

                is LabelPart.Slot -> {
                    val slotDef = part.slot
                    val arg = node.args[slotDef.key]
                    val nested = (arg as? Arg.Blk)?.node
                    if (nested != null && level < MAX_INLINE_DEPTH) {
                        val nestedDef = BlockCatalog[nested.type] ?: unknownDef(nested.type)
                        val innerPad = m.chipPadX
                        val inner = layoutParts(
                            nested, nestedDef,
                            cursor + innerPad,
                            blockTop + (blockHeight - chipH) / 2f,
                            chipH,
                            level + 1
                        )
                        val w = inner.totalWidth + innerPad * 2
                        out += LaidPart.Nested(
                            node = nested,
                            def = nestedDef,
                            x = cursor,
                            y = blockTop + (blockHeight - chipH) / 2f,
                            width = w,
                            height = chipH,
                            depth = level + 1,
                            parts = inner.parts
                        )
                        // The whole inline block is also a tap target that reopens its slot editor.
                        slots += LaidSlot(
                            blockId = node.id,
                            slot = slotDef,
                            display = "",
                            nested = nested,
                            x = cursor,
                            y = blockTop + (blockHeight - chipH) / 2f,
                            width = w,
                            height = chipH,
                            depth = level,
                            color = nestedDef.color()
                        )
                        cursor += w
                    } else {
                        val display = displayValue(node, slotDef, nested)
                        val w = m.textWidth(display, fontSize) + m.chipPadX * 2
                        val laid = LaidSlot(
                            blockId = node.id,
                            slot = slotDef,
                            display = display,
                            nested = nested,
                            x = cursor,
                            y = blockTop + (blockHeight - chipH) / 2f,
                            width = w,
                            height = chipH,
                            depth = level,
                            color = null
                        )
                        slots += laid
                        out += LaidPart.Chip(laid)
                        cursor += w
                    }
                }
            }
        }
        return Parts(out, (cursor - startX).coerceAtLeast(0f))
    }

    /** Text shown inside a chip. Ids become human names via the metrics' resolver. */
    private fun displayValue(node: BlockNode, slot: SlotDef, nested: BlockNode?): String {
        if (nested != null) return BlockCatalog[nested.type]?.plainLabel ?: nested.type
        val raw = (node.args[slot.key] as? Arg.Lit)?.value ?: slot.default
        return m.slotLabel(slot, raw)
    }

    private companion object {
        /** Horizontal offset of the connector rail inside a lane. */
        const val INSET = 22f

        /** Deeper expressions collapse to a text chip rather than shrinking into illegibility. */
        const val MAX_INLINE_DEPTH = 2
    }
}

private data class Size(val width: Float, val height: Float)

private fun BlockDef.color(): Int = category.color

private fun unknownDef(type: String) = BlockDef(
    type = type,
    category = com.blockforge.engine.blocks.BlockCategory.CONTROL,
    shape = BlockShape.STACK,
    label = "blok tidak dikenal ($type)"
)
