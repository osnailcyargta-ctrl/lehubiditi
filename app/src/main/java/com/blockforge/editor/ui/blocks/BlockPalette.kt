package com.blockforge.editor.ui.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.blockforge.editor.ui.theme.ForgeColors
import com.blockforge.engine.blocks.BlockCatalog
import com.blockforge.engine.blocks.BlockCategory
import com.blockforge.engine.blocks.BlockDef
import com.blockforge.engine.blocks.BlockShape

/**
 * The block drawer.
 *
 * Tapping a block inserts it at the pending insertion point rather than starting a drag — on a
 * phone, aiming a dragged block at a lane is fiddly, and one tap per block is how a script actually
 * gets built.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockPaletteSheet(
    /** Restricts the list, e.g. to reporters when filling a value slot. */
    filter: (BlockDef) -> Boolean = { true },
    title: String = "Tambah blok",
    subtitle: String? = null,
    onPick: (BlockDef) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<BlockCategory?>(null) }

    val results = remember(query, category) {
        BlockCatalog.search(query)
            .filter(filter)
            .filter { category == null || it.category == category }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ForgeColors.Panel
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = ForgeColors.TextPrimary)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = ForgeColors.TextMuted)
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari blok…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    CategoryChip("Semua", ForgeColors.Accent, category == null) { category = null }
                }
                items(BlockCatalog.categories) { c ->
                    CategoryChip(c.title, Color(c.color), category == c) {
                        category = if (category == c) null else c
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.type }) { def ->
                    BlockPreviewRow(def) { onPick(def) }
                }
                if (results.isEmpty()) {
                    item {
                        Text(
                            "Tidak ada blok yang cocok.",
                            color = ForgeColors.TextMuted,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) color.copy(alpha = 0.22f) else ForgeColors.PanelRaised)
            .border(1.dp, if (selected) color else ForgeColors.Outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(
            label,
            color = if (selected) ForgeColors.TextPrimary else ForgeColors.TextMuted,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun BlockPreviewRow(def: BlockDef, onClick: () -> Unit) {
    val color = Color(def.category.color)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            Modifier
                .size(width = 8.dp, height = 34.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Column(Modifier.padding(start = 12.dp).fillMaxWidth()) {
            Text(
                def.plainLabel,
                color = ForgeColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                shapeHint(def),
                color = ForgeColors.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun shapeHint(def: BlockDef): String = when (def.shape) {
    BlockShape.HAT -> "${def.category.title} · blok kepala, membuka lane utama"
    BlockShape.BRANCH -> "${def.category.title} · membuka ${def.branches.size} cabang ke kanan"
    BlockShape.TERMINAL -> "${def.category.title} · menutup lane"
    BlockShape.REPORTER -> "${def.category.title} · nilai"
    BlockShape.BOOLEAN -> "${def.category.title} · benar/salah"
    BlockShape.STACK -> def.help.ifEmpty { def.category.title }
}

/** A slim strip of the most-used blocks, always visible above the canvas. */
@Composable
fun QuickBlockBar(onPick: (BlockDef) -> Unit, modifier: Modifier = Modifier) {
    val quick = remember {
        listOf(
            "event.frame", "control.if", "control.forever", "control.wait",
            "motion.move_xy", "var.change", "control.broadcast", "sound.play"
        ).mapNotNull { BlockCatalog[it] }
    }
    LazyRow(
        modifier = modifier.fillMaxWidth().height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(quick, key = { it.type }) { def ->
            val color = Color(def.category.color)
            Text(
                def.plainLabel,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onPick(def) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
    }
}
