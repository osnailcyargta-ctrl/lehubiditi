package com.blockforge.editor.ui.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.blockforge.editor.ui.theme.ForgeColors
import com.blockforge.engine.blocks.BlockCatalog
import com.blockforge.engine.blocks.BlockDef
import com.blockforge.engine.blocks.BlockShape
import com.blockforge.engine.blocks.SlotDef
import com.blockforge.engine.blocks.SlotKind
import com.blockforge.engine.model.Arg
import com.blockforge.engine.model.AssetKind
import com.blockforge.engine.model.BlockNode
import com.blockforge.engine.model.GameProject

/**
 * The value editor for one block slot.
 *
 * A slot can hold a typed value *or* another block, so this sheet always offers both: the picker
 * appropriate to the slot's kind at the top, and "pakai blok" underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotEditorSheet(
    project: GameProject,
    node: BlockNode,
    slot: SlotDef,
    onSetLiteral: (String) -> Unit,
    onSetBlock: (String) -> Unit,
    onClear: () -> Unit,
    onCreateVariable: () -> Unit,
    onCreateMessage: (String) -> Unit,
    onImportAsset: (AssetKind) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val current = (node.args[slot.key] as? Arg.Lit)?.value ?: slot.default
    val nested = (node.args[slot.key] as? Arg.Blk)?.node
    var showBlockPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = ForgeColors.Panel) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Text(
                slotTitle(slot),
                style = MaterialTheme.typography.titleLarge,
                color = ForgeColors.TextPrimary
            )
            Text(
                BlockCatalog[node.type]?.plainLabel ?: node.type,
                style = MaterialTheme.typography.bodySmall,
                color = ForgeColors.TextMuted,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (nested != null) {
                NestedBlockBanner(nested) { onClear() }
            }

            when (slot.kind) {
                SlotKind.NUMBER -> NumberEditor(current, onSetLiteral)
                SlotKind.TEXT -> TextEditor(current, onSetLiteral)
                SlotKind.BOOLEAN -> BooleanEditor(current, onSetLiteral)

                SlotKind.CHOICE, SlotKind.KEY -> OptionList(
                    options = slot.choices.map { it.value to it.label },
                    selected = current,
                    onPick = onSetLiteral
                )

                SlotKind.VARIABLE -> OptionList(
                    options = project.variables.map { it.id to "${it.name}  ·  ${it.kind.name.lowercase()}" },
                    selected = current,
                    onPick = onSetLiteral,
                    emptyText = "Belum ada variabel.",
                    action = Pair("Buat variabel", onCreateVariable)
                )

                SlotKind.MESSAGE -> MessageEditor(project, current, onSetLiteral, onCreateMessage)

                SlotKind.OBJECT -> OptionList(
                    options = listOf("" to "objek ini") +
                        project.scenes.flatMap { scene ->
                            scene.objects.map { it.id to "${it.name}  ·  ${scene.name}" }
                        },
                    selected = current,
                    onPick = onSetLiteral
                )

                SlotKind.IMAGE -> OptionList(
                    options = project.assets.filter { it.kind == AssetKind.IMAGE }.map { it.id to it.name },
                    selected = current,
                    onPick = onSetLiteral,
                    emptyText = "Belum ada gambar di proyek ini.",
                    action = Pair("Impor gambar") { onImportAsset(AssetKind.IMAGE) }
                )

                SlotKind.AUDIO -> OptionList(
                    options = project.assets.filter { it.kind == AssetKind.AUDIO }.map { it.id to it.name },
                    selected = current,
                    onPick = onSetLiteral,
                    emptyText = "Belum ada suara di proyek ini.",
                    action = Pair("Impor MP3") { onImportAsset(AssetKind.AUDIO) }
                )

                SlotKind.SCENE -> OptionList(
                    options = project.scenes.map { it.id to it.name },
                    selected = current,
                    onPick = onSetLiteral
                )

                SlotKind.COLOR -> ColorEditor(current, onSetLiteral)
            }

            if (slot.acceptsBlock) {
                OutlinedButton(
                    onClick = { showBlockPicker = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Pakai blok di slot ini", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showBlockPicker) {
        val wantsBoolean = slot.kind == SlotKind.BOOLEAN
        BlockPaletteSheet(
            filter = { def ->
                if (wantsBoolean) def.shape == BlockShape.BOOLEAN
                else def.shape == BlockShape.REPORTER || def.shape == BlockShape.BOOLEAN
            },
            title = if (wantsBoolean) "Pilih blok syarat" else "Pilih blok nilai",
            subtitle = "Blok ini akan menempel di slot '${slot.key}'",
            onPick = { def ->
                onSetBlock(def.type)
                showBlockPicker = false
                onDismiss()
            },
            onDismiss = { showBlockPicker = false }
        )
    }
}

private fun slotTitle(slot: SlotDef): String = when (slot.kind) {
    SlotKind.NUMBER -> "Angka"
    SlotKind.TEXT -> "Teks"
    SlotKind.BOOLEAN -> "Syarat benar/salah"
    SlotKind.CHOICE -> "Pilihan"
    SlotKind.KEY -> "Tombol"
    SlotKind.VARIABLE -> "Variabel"
    SlotKind.MESSAGE -> "Pesan siaran"
    SlotKind.OBJECT -> "Objek"
    SlotKind.IMAGE -> "Gambar"
    SlotKind.AUDIO -> "Suara"
    SlotKind.SCENE -> "Scene"
    SlotKind.COLOR -> "Warna"
}

@Composable
private fun NestedBlockBanner(nested: BlockNode, onClear: () -> Unit) {
    val def: BlockDef? = BlockCatalog[nested.type]
    val color = def?.let { Color(it.category.color) } ?: ForgeColors.Accent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Slot ini berisi blok", color = ForgeColors.TextMuted, style = MaterialTheme.typography.labelSmall)
            Text(
                def?.plainLabel ?: nested.type,
                color = ForgeColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        TextButton(onClick = onClear) {
            Icon(Icons.Default.Close, contentDescription = null)
            Text(" Lepas")
        }
    }
}

@Composable
private fun NumberEditor(current: String, onSet: (String) -> Unit) {
    var text by remember(current) { mutableStateOf(current) }
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onSet(it) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            items(listOf(-100, -10, -1, 1, 10, 100)) { step ->
                OutlinedButton(onClick = {
                    val next = ((text.toDoubleOrNull() ?: 0.0) + step)
                    val formatted = if (next == next.toLong().toDouble()) next.toLong().toString() else next.toString()
                    text = formatted
                    onSet(formatted)
                }) { Text(if (step > 0) "+$step" else "$step") }
            }
        }
    }
}

@Composable
private fun TextEditor(current: String, onSet: (String) -> Unit) {
    var text by remember(current) { mutableStateOf(current) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onSet(it) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BooleanEditor(current: String, onSet: (String) -> Unit) {
    OptionList(
        options = listOf("true" to "benar", "false" to "salah"),
        selected = current,
        onPick = onSet
    )
}

@Composable
private fun MessageEditor(
    project: GameProject,
    current: String,
    onSet: (String) -> Unit,
    onCreate: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    OptionList(
        options = project.messages.map { it to it },
        selected = current,
        onPick = onSet,
        emptyText = "Belum ada pesan."
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text("pesan baru") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                if (draft.isNotBlank()) {
                    onCreate(draft.trim()); onSet(draft.trim()); draft = ""
                }
            },
            modifier = Modifier.padding(start = 8.dp)
        ) { Text("Buat") }
    }
}

@Composable
private fun ColorEditor(current: String, onSet: (String) -> Unit) {
    val swatches = listOf(
        "#4FC3F7", "#FFD166", "#EF6F6C", "#3FB950", "#A66BFF",
        "#22B8CF", "#F2861D", "#FFFFFF", "#2E7D5B", "#1B2231"
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(swatches) { hex ->
            val selected = hex.equals(current, ignoreCase = true)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(hex)))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) Color.White else ForgeColors.Outline,
                        shape = CircleShape
                    )
                    .clickable { onSet(hex) }
            )
        }
    }
}

@Composable
private fun OptionList(
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
    emptyText: String = "Tidak ada pilihan.",
    action: Pair<String, () -> Unit>? = null
) {
    if (options.isEmpty()) {
        Column {
            Text(emptyText, color = ForgeColors.TextMuted, modifier = Modifier.padding(vertical = 12.dp))
            action?.let { (label, onClick) ->
                Button(onClick = onClick) { Text(label) }
            }
        }
        return
    }
    Column {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(options, key = { it.first }) { (value, label) ->
                val isSelected = value == selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) ForgeColors.Accent.copy(alpha = 0.18f) else ForgeColors.PanelRaised)
                        .border(
                            1.dp,
                            if (isSelected) ForgeColors.Accent else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onPick(value) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        label,
                        color = ForgeColors.TextPrimary,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        action?.let { (label, onClick) ->
            TextButton(onClick = onClick, modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  $label")
            }
        }
    }
}
