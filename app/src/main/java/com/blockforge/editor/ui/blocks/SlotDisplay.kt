package com.blockforge.editor.ui.blocks

import com.blockforge.engine.blocks.SlotDef
import com.blockforge.engine.blocks.SlotKind
import com.blockforge.engine.model.GameProject

/**
 * Chips store ids, people read names. This turns `var_9f3` into `skor` for the canvas, and is the
 * only place that mapping lives so a renamed variable updates everywhere at once.
 */
fun slotDisplayResolver(project: GameProject): (SlotDef, String) -> String = resolver@{ slot, raw ->
    when (slot.kind) {
        SlotKind.CHOICE, SlotKind.KEY ->
            slot.choices.firstOrNull { it.value == raw }?.label ?: raw.ifEmpty { "—" }

        SlotKind.VARIABLE ->
            project.variable(raw)?.name ?: project.variables.firstOrNull()?.name ?: "pilih variabel"

        SlotKind.IMAGE ->
            project.asset(raw)?.name ?: "pilih gambar"

        SlotKind.AUDIO ->
            project.asset(raw)?.name ?: "pilih suara"

        SlotKind.OBJECT -> {
            if (raw.isEmpty()) "objek ini"
            else project.scenes.firstNotNullOfOrNull { it.obj(raw) }?.name ?: "objek ini"
        }

        SlotKind.SCENE ->
            project.scene(raw)?.name ?: "pilih scene"

        SlotKind.BOOLEAN ->
            if (raw.equals("true", true) || raw == "1") "benar" else "salah"

        SlotKind.COLOR -> raw.ifEmpty { "#000000" }

        else -> raw.ifEmpty { "—" }
    }
}
