package com.blockforge.editor.ui.blocks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blockforge.editor.EditorViewModel
import com.blockforge.editor.SlotTarget
import com.blockforge.editor.ui.play.GamePlayer
import com.blockforge.editor.ui.theme.ForgeColors
import com.blockforge.editor.ui.theme.ForgeIcons
import com.blockforge.engine.blocks.BlockCatalog
import com.blockforge.engine.blocks.BlockShape
import com.blockforge.engine.blocks.BlockTree
import com.blockforge.engine.model.AssetKind

/**
 * The block-coding tab: toolbar, the lane canvas, a floating live preview, and a contextual action
 * bar for whichever block is selected.
 */
@Composable
fun BlocksPanel(
    vm: EditorViewModel,
    onImportAsset: (AssetKind) -> Unit,
    onCreateVariable: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPalette by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    var previewToken by remember { mutableIntStateOf(0) }

    val slotDisplay = remember(vm.project) { slotDisplayResolver(vm.project) }
    val selectedNode = vm.selectedBlockId?.let { BlockTree.findNode(vm.scripts, it) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Toolbar(vm, preview, onTogglePreview = { preview = !preview; if (preview) previewToken++ })

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (vm.selectedObject == null) {
                    EmptyState("Belum ada objek", "Buat objek di tab Scene lebih dulu — skrip selalu menempel pada satu objek.")
                } else if (vm.scripts.isEmpty()) {
                    EmptyState(
                        "Lane masih kosong",
                        "Tambahkan blok kepala seperti 'saat game dimulai' untuk membuka lane utama."
                    )
                } else {
                    BlockCanvas(
                        scripts = vm.scripts,
                        activeBlocks = vm.activeBlocks,
                        selectedId = vm.selectedBlockId,
                        insertion = vm.insertion,
                        slotLabel = slotDisplay,
                        onSelectBlock = { vm.selectedBlockId = it },
                        onSelectGap = { laneId, index ->
                            vm.insertion = com.blockforge.editor.InsertionTarget(laneId, index)
                            showPalette = true
                        },
                        onSlotTap = { blockId, key -> vm.slotTarget = SlotTarget(blockId, key) },
                        onMoveScript = { id, x, y, record -> vm.moveScript(id, x, y, record) },
                        onMoveBlock = { id, laneId, index -> vm.moveBlock(id, laneId, index) },
                        onCommitEdit = { vm.save() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (preview) {
                    // A small live window: blocks on the canvas glow as this runs.
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                            .size(width = 220.dp, height = 132.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(2.dp, ForgeColors.Accent, RoundedCornerShape(14.dp))
                    ) {
                        GamePlayer(
                            project = vm.project,
                            resDir = vm.store.resDir(vm.projectId),
                            reloadToken = previewToken,
                            paused = false,
                            onActiveBlocks = { vm.activeBlocks = it },
                            onError = { vm.toast(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            QuickBlockBar(
                onPick = { def -> vm.addBlockAtInsertion(def.type) },
                modifier = Modifier
                    .background(ForgeColors.Panel)
                    .padding(horizontal = 12.dp)
            )
        }

        ExtendedFloatingActionButton(
            onClick = { showPalette = true },
            containerColor = ForgeColors.Accent,
            contentColor = Color(0xFF04121A),
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp, bottom = 74.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("  Blok", fontWeight = FontWeight.Bold)
        }

        AnimatedVisibility(
            visible = selectedNode != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
        ) {
            selectedNode?.let { node -> SelectedBlockBar(vm, node.id, node.type) }
        }
    }

    if (showPalette) {
        val hasInsertion = vm.insertion != null
        BlockPaletteSheet(
            filter = { def -> !def.isValue },
            title = "Tambah blok",
            subtitle = if (hasInsertion) "Akan disisipkan di titik + yang dipilih"
            else "Blok kepala membuat lane baru; pilih titik + dulu untuk blok lain",
            onPick = { def ->
                vm.addBlockAtInsertion(def.type)
                showPalette = false
            },
            onDismiss = { showPalette = false }
        )
    }

    vm.slotTarget?.let { target ->
        val node = BlockTree.findNode(vm.scripts, target.blockId)
        val slot = node?.let { BlockCatalog[it.type]?.slot(target.slotKey) }
        if (node != null && slot != null) {
            SlotEditorSheet(
                project = vm.project,
                node = node,
                slot = slot,
                onSetLiteral = { vm.setSlotLiteral(target.blockId, target.slotKey, it) },
                onSetBlock = { vm.setSlotBlock(target.blockId, target.slotKey, it) },
                onClear = { vm.clearSlot(target.blockId, target.slotKey) },
                onCreateVariable = onCreateVariable,
                onCreateMessage = { vm.addMessage(it) },
                onImportAsset = onImportAsset,
                onDismiss = { vm.slotTarget = null }
            )
        } else {
            vm.slotTarget = null
        }
    }
}

@Composable
private fun Toolbar(vm: EditorViewModel, preview: Boolean, onTogglePreview: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(ForgeColors.Panel)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            vm.scene.objects.forEach { obj ->
                FilterChip(
                    selected = obj.id == vm.objectId,
                    onClick = { vm.selectObject(obj.id) },
                    leadingIcon = {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(obj.fallbackColor)))
                    },
                    label = { Text("${obj.name} (${obj.scripts.size})") }
                )
            }
        }
        IconButton(onClick = { vm.undo() }, enabled = vm.canUndo) {
            Icon(ForgeIcons.Undo, contentDescription = "Urungkan", tint = ForgeColors.TextMuted)
        }
        IconButton(onClick = { vm.redo() }, enabled = vm.canRedo) {
            Icon(ForgeIcons.Redo, contentDescription = "Ulangi", tint = ForgeColors.TextMuted)
        }
        IconButton(onClick = onTogglePreview) {
            Icon(
                if (preview) ForgeIcons.Stop else Icons.Default.PlayArrow,
                contentDescription = "Pratinjau langsung",
                tint = if (preview) ForgeColors.Danger else ForgeColors.Success
            )
        }
    }
}

@Composable
private fun SelectedBlockBar(vm: EditorViewModel, blockId: String, type: String) {
    val def = BlockCatalog[type]
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(ForgeColors.PanelRaised)
            .border(1.dp, ForgeColors.Outline, RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            Modifier
                .width(6.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(def?.category?.color ?: 0xFF4FC3F7.toInt()))
        )
        Text(
            def?.plainLabel?.take(24) ?: type,
            color = ForgeColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        if (def?.shape == BlockShape.BRANCH) {
            AssistChip(
                onClick = { vm.addBranch(blockId) },
                leadingIcon = { Icon(ForgeIcons.Branch, contentDescription = null) },
                label = { Text("Cabang") }
            )
        }
        if (def?.shape != BlockShape.HAT) {
            TextButton(onClick = { vm.wrapInControl(blockId, "control.if") }) { Text("Bungkus jika") }
        }
        IconButton(onClick = { vm.duplicateBlock(blockId) }) {
            Icon(ForgeIcons.Copy, contentDescription = "Duplikat", tint = ForgeColors.TextMuted)
        }
        IconButton(onClick = { vm.deleteBlock(blockId) }) {
            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = ForgeColors.Danger)
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = ForgeColors.TextPrimary)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = ForgeColors.TextMuted,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
