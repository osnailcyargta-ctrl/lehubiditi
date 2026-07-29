package com.blockforge.editor.ui.assets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.blockforge.engine.model.VariableKind
import com.blockforge.engine.model.VariableScope

/**
 * Project tab: sprites, sounds, variables, broadcast messages and scenes.
 *
 * Importing is a plain SAF pick — the file is copied into the project folder immediately, so the
 * project stays self-contained and survives the picked Uri's permission expiring.
 */
@Composable
fun AssetsPanel(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.importAsset(it, AssetKind.IMAGE) }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.importAsset(it, AssetKind.AUDIO) }
    }

    var showVariableDialog by remember { mutableStateOf(false) }
    var newMessage by remember { mutableStateOf("") }
    var newScene by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeader("Gambar / Sprite") }
        item {
            OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Icon(ForgeIcons.Picture, contentDescription = null)
                Text("  Unggah gambar (PNG / JPG / WebP)")
            }
        }
        items(vm.project.assets.filter { it.kind == AssetKind.IMAGE }, key = { it.id }) { asset ->
            AssetRow(
                title = asset.name,
                subtitle = asset.fileName,
                icon = ForgeIcons.Picture,
                tint = ForgeColors.Accent,
                onRename = { vm.renameAsset(asset.id, it) },
                onDelete = { vm.deleteAsset(asset.id) }
            )
        }

        item { SectionHeader("Suara / Musik") }
        item {
            OutlinedButton(onClick = { audioPicker.launch("audio/*") }, modifier = Modifier.fillMaxWidth()) {
                Icon(ForgeIcons.AudioTrack, contentDescription = null)
                Text("  Unggah MP3 / WAV / OGG")
            }
        }
        items(vm.project.assets.filter { it.kind == AssetKind.AUDIO }, key = { it.id }) { asset ->
            AssetRow(
                title = asset.name,
                subtitle = asset.fileName,
                icon = ForgeIcons.AudioTrack,
                tint = Color(0xFFE0559B),
                onRename = { vm.renameAsset(asset.id, it) },
                onDelete = { vm.deleteAsset(asset.id) }
            )
        }

        item { SectionHeader("Variabel") }
        item {
            OutlinedButton(onClick = { showVariableDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  Buat variabel")
            }
        }
        items(vm.project.variables, key = { it.id }) { variable ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(variable.name, color = ForgeColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${variable.kind.name.lowercase()} · ${if (variable.scope == VariableScope.GLOBAL) "global" else "per objek"} · awal ${variable.initial}",
                            color = ForgeColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(
                        checked = variable.showOnScreen,
                        onCheckedChange = { on -> vm.updateVariable(variable.id) { it.copy(showOnScreen = on) } }
                    )
                    IconButton(onClick = { vm.deleteVariable(variable.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = ForgeColors.Danger)
                    }
                }
            }
        }

        item { SectionHeader("Pesan siaran") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newMessage,
                    onValueChange = { newMessage = it },
                    placeholder = { Text("nama pesan") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { vm.addMessage(newMessage); newMessage = "" },
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("Tambah") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                vm.project.messages.take(8).forEach { msg ->
                    FilterChip(selected = false, onClick = {}, label = { Text(msg) })
                }
            }
        }

        item { SectionHeader("Scene") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newScene,
                    onValueChange = { newScene = it },
                    placeholder = { Text("nama scene baru") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { vm.addScene(newScene); newScene = "" },
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("Tambah") }
            }
        }
        items(vm.project.scenes, key = { it.id }) { scene ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { vm.selectScene(scene.id) }
                    ) {
                        Text(
                            scene.name,
                            color = if (scene.id == vm.sceneId) ForgeColors.Accent else ForgeColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${scene.objects.size} objek" + if (scene.id == vm.project.startSceneId) " · scene awal" else "",
                            color = ForgeColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    TextButton(onClick = { vm.setStartScene(scene.id) }) { Text("Jadikan awal") }
                    IconButton(onClick = { vm.deleteScene(scene.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = ForgeColors.Danger)
                    }
                }
            }
        }

        item { Box(Modifier.size(40.dp)) }
    }

    if (showVariableDialog) {
        NewVariableDialog(
            onCreate = { name, kind, scope, initial ->
                vm.addVariable(name, kind, scope, initial)
                showVariableDialog = false
            },
            onDismiss = { showVariableDialog = false }
        )
    }
}

@Composable
private fun NewVariableDialog(
    onCreate: (String, VariableKind, VariableScope, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var initial by remember { mutableStateOf("0") }
    var kind by remember { mutableStateOf(VariableKind.NUMBER) }
    var scope by remember { mutableStateOf(VariableScope.GLOBAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onCreate(name, kind, scope, initial) }, enabled = name.isNotBlank()) {
                Text("Buat")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        title = { Text("Variabel baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = initial,
                    onValueChange = { initial = it },
                    label = { Text("Nilai awal") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VariableKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.name.lowercase()) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = scope == VariableScope.GLOBAL,
                        onClick = { scope = VariableScope.GLOBAL },
                        label = { Text("global") }
                    )
                    FilterChip(
                        selected = scope == VariableScope.OBJECT,
                        onClick = { scope = VariableScope.OBJECT },
                        label = { Text("per objek") }
                    )
                }
            }
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        color = ForgeColors.Accent,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp)
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ForgeColors.PanelRaised)
            .border(1.dp, ForgeColors.Outline, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) { content() }
}

@Composable
private fun AssetRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(title) { mutableStateOf(title) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = tint) }

            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                if (editing) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        title,
                        color = ForgeColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { editing = true }
                    )
                    Text(subtitle, color = ForgeColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (editing) {
                TextButton(onClick = { onRename(draft); editing = false }) { Text("Simpan") }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = ForgeColors.Danger)
            }
        }
    }
}
