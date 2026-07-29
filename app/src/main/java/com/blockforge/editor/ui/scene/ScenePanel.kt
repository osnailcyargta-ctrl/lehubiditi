package com.blockforge.editor.ui.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.blockforge.editor.EditorViewModel
import com.blockforge.editor.ui.theme.ForgeColors
import com.blockforge.editor.ui.theme.ForgeIcons
import com.blockforge.engine.model.AssetKind
import com.blockforge.engine.model.GameObject
import com.blockforge.engine.model.ObjectShape

/** Scene tab: object strip on top, live scene preview below, inspector in a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenePanel(vm: EditorViewModel, modifier: Modifier = Modifier) {
    var showInspector by remember { mutableStateOf(false) }
    val selected = vm.selectedObject

    Column(modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(ForgeColors.Panel)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                vm.scene.objects.forEach { obj ->
                    FilterChip(
                        selected = obj.id == vm.objectId,
                        onClick = { vm.selectObject(obj.id) },
                        leadingIcon = {
                            Box(
                                Modifier.size(14.dp).clip(CircleShape).background(Color(obj.fallbackColor))
                            )
                        },
                        label = { Text(obj.name) }
                    )
                }
                AssistChip(
                    onClick = { vm.addObject() },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text("Objek") }
                )
            }
            IconButton(onClick = { showInspector = true }, enabled = selected != null) {
                Icon(ForgeIcons.Tune, contentDescription = "Properti objek", tint = ForgeColors.Accent)
            }
        }

        SceneCanvas(
            project = vm.project,
            scene = vm.scene,
            selectedId = vm.objectId,
            assetFile = { fileName -> vm.store.assetFile(vm.projectId, fileName) },
            onSelect = { vm.selectObject(it) },
            onMove = { id, x, y, record ->
                vm.updateObject(id, record = record, persist = record) { it.copy(x = x, y = y) }
            },
            onCommit = { vm.save() },
            modifier = Modifier.weight(1f)
        )
    }

    if (showInspector && selected != null) {
        ModalBottomSheet(
            onDismissRequest = { showInspector = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ForgeColors.Panel
        ) {
            ObjectInspector(vm, selected) { showInspector = false }
        }
    }
}

@Composable
private fun ObjectInspector(vm: EditorViewModel, obj: GameObject, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Properti objek", style = MaterialTheme.typography.titleLarge, color = ForgeColors.TextPrimary)
            Box(Modifier.weight(1f))
            IconButton(onClick = { vm.duplicateObject(obj.id) }) {
                Icon(ForgeIcons.Copy, contentDescription = "Duplikat", tint = ForgeColors.TextMuted)
            }
            IconButton(onClick = { vm.deleteObject(obj.id); onClose() }) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = ForgeColors.Danger)
            }
        }

        Section("Identitas")
        TextRow("Nama", obj.name) { v -> vm.updateObject(obj.id) { it.copy(name = v) } }
        TextRow("Tag (dipakai blok tabrakan)", obj.tag) { v -> vm.updateObject(obj.id) { it.copy(tag = v) } }

        Section("Transformasi")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberRow("X", obj.x, Modifier.weight(1f)) { v -> vm.updateObject(obj.id) { it.copy(x = v) } }
            NumberRow("Y", obj.y, Modifier.weight(1f)) { v -> vm.updateObject(obj.id) { it.copy(y = v) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberRow("Lebar", obj.width, Modifier.weight(1f)) { v ->
                vm.updateObject(obj.id) { it.copy(width = v.coerceAtLeast(1f)) }
            }
            NumberRow("Tinggi", obj.height, Modifier.weight(1f)) { v ->
                vm.updateObject(obj.id) { it.copy(height = v.coerceAtLeast(1f)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberRow("Rotasi", obj.rotation, Modifier.weight(1f)) { v -> vm.updateObject(obj.id) { it.copy(rotation = v) } }
            NumberRow("Lapisan Z", obj.zIndex.toFloat(), Modifier.weight(1f)) { v ->
                vm.updateObject(obj.id) { it.copy(zIndex = v.toInt()) }
            }
        }

        Section("Tampilan")
        CheckRow("Terlihat", obj.visible) { v -> vm.updateObject(obj.id) { it.copy(visible = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Bentuk", color = ForgeColors.TextMuted, modifier = Modifier.width(70.dp))
            FilterChip(
                selected = obj.shape == ObjectShape.RECT,
                onClick = { vm.updateObject(obj.id) { it.copy(shape = ObjectShape.RECT) } },
                label = { Text("Kotak") }
            )
            FilterChip(
                selected = obj.shape == ObjectShape.CIRCLE,
                onClick = { vm.updateObject(obj.id) { it.copy(shape = ObjectShape.CIRCLE) } },
                label = { Text("Lingkaran") }
            )
        }

        Text("Sprite", color = ForgeColors.TextMuted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = obj.spriteAssetId == null,
                onClick = { vm.updateObject(obj.id) { it.copy(spriteAssetId = null) } },
                label = { Text("Warna polos") }
            )
            vm.project.assets.filter { it.kind == AssetKind.IMAGE }.forEach { asset ->
                FilterChip(
                    selected = obj.spriteAssetId == asset.id,
                    onClick = { vm.updateObject(obj.id) { it.copy(spriteAssetId = asset.id) } },
                    label = { Text(asset.name) }
                )
            }
        }

        Section("Fisika")
        CheckRow("Aktifkan fisika", obj.physics.enabled) { v ->
            vm.updateObject(obj.id) { it.copy(physics = it.physics.copy(enabled = v)) }
        }
        if (obj.physics.enabled) {
            CheckRow("Diam (lantai / dinding)", obj.physics.static) { v ->
                vm.updateObject(obj.id) { it.copy(physics = it.physics.copy(static = v)) }
            }
            CheckRow("Padat (bisa ditabrak)", obj.physics.solid) { v ->
                vm.updateObject(obj.id) { it.copy(physics = it.physics.copy(solid = v)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberRow("Gravitasi ×", obj.physics.gravityScale, Modifier.weight(1f)) { v ->
                    vm.updateObject(obj.id) { it.copy(physics = it.physics.copy(gravityScale = v)) }
                }
                NumberRow("Pantul", obj.physics.bounce, Modifier.weight(1f)) { v ->
                    vm.updateObject(obj.id) { it.copy(physics = it.physics.copy(bounce = v.coerceIn(0f, 1f))) }
                }
            }
            NumberRow("Gesekan (0–1)", obj.physics.friction) { v ->
                vm.updateObject(obj.id) { it.copy(physics = it.physics.copy(friction = v.coerceIn(0f, 1f))) }
            }
        }

        Text(
            "Skrip objek ini: ${obj.scripts.size} lane utama",
            color = ForgeColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 18.dp)
        )
    }
}

// ---- small field widgets -----------------------------------------------------------------------

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        color = ForgeColors.Accent,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun TextRow(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
private fun NumberRow(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit
) {
    // Keeps the raw string so typing "1." or "-" does not snap back mid-edit.
    var text by remember(value) { mutableStateOf(formatNumber(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            raw.replace(',', '.').toFloatOrNull()?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

private fun formatNumber(value: Float): String =
    if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 2.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, color = ForgeColors.TextPrimary)
    }
}

/** Small reusable outlined container used by other panels. */
@Composable
fun PanelCard(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ForgeColors.PanelRaised)
            .border(1.dp, ForgeColors.Outline, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) { content() }
}
