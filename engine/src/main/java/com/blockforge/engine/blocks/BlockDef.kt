package com.blockforge.engine.blocks

/** Where a block's body lanes are drawn relative to the block itself. */
enum class BranchDirection {
    /** Hat blocks own the one main lane, which flows straight down. */
    DOWN,

    /** Everything else opens a *new lane to the right*, connected by an elbow. */
    RIGHT
}

enum class BlockShape {
    /** Starts a script. Has exactly one DOWN branch: the main lane. */
    HAT,

    /** Plain statement — does something, then the lane continues below it. */
    STACK,

    /** Statement that opens one or more lanes to the right. */
    BRANCH,

    /** Ends the lane it lives in; nothing after it runs. */
    TERMINAL,

    /** Fits in a value slot and reports a number or text. */
    REPORTER,

    /** Fits in a condition slot and reports true/false. */
    BOOLEAN
}

enum class BlockCategory(val title: String, val color: Int, val accent: Int) {
    EVENT("Kejadian", 0xFFF2C037.toInt(), 0xFFFFE08A.toInt()),
    CONTROL("Kontrol", 0xFFF2861D.toInt(), 0xFFFFC182.toInt()),
    MOTION("Gerak", 0xFF4C8DFF.toInt(), 0xFFA9C8FF.toInt()),
    LOOKS("Tampilan", 0xFFA66BFF.toInt(), 0xFFD4B8FF.toInt()),
    SOUND("Suara", 0xFFE0559B.toInt(), 0xFFF7A9CD.toInt()),
    VARIABLE("Variabel", 0xFFFF7043.toInt(), 0xFFFFB49B.toInt()),
    SENSING("Sensor", 0xFF22B8CF.toInt(), 0xFF8FE3EE.toInt()),
    OPERATOR("Operator", 0xFF3FB950.toInt(), 0xFF9BE8A6.toInt()),
    GAME("Game", 0xFF14B8A6.toInt(), 0xFF8BE3D9.toInt())
}

/** What kind of picker the editor opens when a slot is tapped. */
enum class SlotKind {
    NUMBER, TEXT, BOOLEAN, CHOICE, VARIABLE, MESSAGE, OBJECT, IMAGE, AUDIO, SCENE, KEY, COLOR
}

data class Choice(val value: String, val label: String)

data class SlotDef(
    val key: String,
    val kind: SlotKind,
    val default: String = "",
    val choices: List<Choice> = emptyList(),
    /** Whether a reporter/boolean block may be dropped into this slot instead of a typed value. */
    val acceptsBlock: Boolean = true
)

data class BranchDef(val label: String = "")

/** One piece of a block's rendered label: either static text or a slot placeholder. */
sealed interface LabelPart {
    data class Text(val text: String) : LabelPart
    data class Slot(val slot: SlotDef) : LabelPart
}

data class BlockDef(
    val type: String,
    val category: BlockCategory,
    val shape: BlockShape,
    /** Label template. `{key}` marks where slot `key` is rendered. */
    val label: String,
    val slots: List<SlotDef> = emptyList(),
    val branches: List<BranchDef> = emptyList(),
    val help: String = ""
) {
    val branchDirection: BranchDirection
        get() = if (shape == BlockShape.HAT) BranchDirection.DOWN else BranchDirection.RIGHT

    val isValue: Boolean get() = shape == BlockShape.REPORTER || shape == BlockShape.BOOLEAN

    /** True when the lane cannot continue past this block. */
    val endsLane: Boolean get() = shape == BlockShape.TERMINAL

    fun slot(key: String): SlotDef? = slots.firstOrNull { it.key == key }

    /** Parsed once — the block canvas re-renders constantly and must not re-parse every frame. */
    val parts: List<LabelPart> by lazy { parseLabel(label, slots) }

    /** Human-readable name with slots stripped, for palette search and accessibility. */
    val plainLabel: String by lazy {
        parts.joinToString(" ") {
            when (it) {
                is LabelPart.Text -> it.text
                is LabelPart.Slot -> "( )"
            }
        }.replace(Regex("\\s+"), " ").trim()
    }
}

private fun parseLabel(label: String, slots: List<SlotDef>): List<LabelPart> {
    val parts = mutableListOf<LabelPart>()
    val buf = StringBuilder()
    var i = 0
    while (i < label.length) {
        val c = label[i]
        if (c == '{') {
            val close = label.indexOf('}', i)
            if (close < 0) {
                buf.append(c); i++; continue
            }
            val key = label.substring(i + 1, close)
            val slot = slots.firstOrNull { it.key == key }
            if (slot != null) {
                val text = buf.toString().trim()
                if (text.isNotEmpty()) parts += LabelPart.Text(text)
                buf.setLength(0)
                parts += LabelPart.Slot(slot)
                i = close + 1
                continue
            }
        }
        buf.append(c)
        i++
    }
    val tail = buf.toString().trim()
    if (tail.isNotEmpty()) parts += LabelPart.Text(tail)
    return parts
}
