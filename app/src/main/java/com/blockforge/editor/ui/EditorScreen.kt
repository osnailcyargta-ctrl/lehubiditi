package com.blockforge.editor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blockforge.editor.EditorTab
import com.blockforge.editor.EditorViewModel
import com.blockforge.editor.export.AndroidExporter
import com.blockforge.editor.ui.assets.AssetsPanel
import com.blockforge.editor.ui.blocks.BlocksPanel
import com.blockforge.editor.ui.play.PlayPanel
import com.blockforge.editor.ui.scene.ScenePanel
import com.blockforge.editor.ui.theme.ForgeColors
import com.blockforge.editor.ui.theme.ForgeIcons
import com.blockforge.engine.model.AssetKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var showProjects by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exporting = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        AndroidExporter.export(context, vm.project, vm.store.resDir(vm.projectId), stream)
                    } ?: error("Tidak bisa membuka berkas tujuan")
                }
            }
            exporting = false
            result.fold(
                onSuccess = { vm.toast("Proyek Android diekspor — ${it.summary()}") },
                onFailure = { vm.toast("Ekspor gagal: ${it.message}") }
            )
        }
    }

    // Asset import can be triggered from deep inside the block editor, so it lives at the top.
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.importAsset(it, AssetKind.IMAGE) }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.importAsset(it, AssetKind.AUDIO) }
    }

    LaunchedEffect(vm.message) {
        vm.message?.let { text ->
            snackbar.showSnackbar(text)
            vm.clearToast()
        }
    }

    Scaffold(
        containerColor = ForgeColors.Canvas,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ForgeColors.Panel,
                    titleContentColor = ForgeColors.TextPrimary
                ),
                title = {
                    Column {
                        Text(vm.project.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${vm.scene.name} · ${vm.scene.objects.size} objek · ${vm.blockCount()} blok",
                            style = MaterialTheme.typography.labelSmall,
                            color = ForgeColors.TextMuted
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showProjects = true }) {
                        Icon(ForgeIcons.Folder, contentDescription = "Proyek", tint = ForgeColors.TextMuted)
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Pengaturan", tint = ForgeColors.TextMuted)
                    }
                    IconButton(
                        onClick = { exportLauncher.launch(AndroidExporter.suggestedFileName(vm.project)) },
                        enabled = !exporting
                    ) {
                        if (exporting) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(ForgeIcons.Android, contentDescription = "Ekspor proyek Android", tint = ForgeColors.Success)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = vm.tab.ordinal,
                containerColor = ForgeColors.Panel,
                contentColor = ForgeColors.Accent
            ) {
                EditorTab.entries.forEach { tab ->
                    Tab(
                        selected = vm.tab == tab,
                        onClick = { vm.tab = tab },
                        text = { Text(tab.title, fontWeight = if (vm.tab == tab) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (vm.tab) {
                    EditorTab.SCENE -> ScenePanel(vm)
                    EditorTab.BLOCKS -> BlocksPanel(
                        vm = vm,
                        onImportAsset = { kind ->
                            if (kind == AssetKind.IMAGE) imagePicker.launch("image/*") else audioPicker.launch("audio/*")
                        },
                        onCreateVariable = { vm.tab = EditorTab.ASSETS }
                    )

                    EditorTab.ASSETS -> AssetsPanel(vm)
                    EditorTab.PLAY -> PlayPanel(vm)
                }
            }
        }
    }

    if (showProjects) ProjectsDialog(vm) { showProjects = false }
    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

@Composable
private fun ProjectsDialog(vm: EditorViewModel, onDismiss: () -> Unit) {
    val entries = remember { vm.projects() }
    var newName by remember { mutableStateOf("") }
    var withStarter by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        title = { Text("Proyek") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entries.forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, fontWeight = FontWeight.SemiBold, color = ForgeColors.TextPrimary)
                            Text(
                                "${entry.sceneCount} scene · ${entry.objectCount} objek",
                                style = MaterialTheme.typography.labelSmall,
                                color = ForgeColors.TextMuted
                            )
                        }
                        TextButton(onClick = { vm.openProject(entry.id); onDismiss() }) { Text("Buka") }
                        TextButton(onClick = { vm.deleteProject(entry.id); onDismiss() }) {
                            Text("Hapus", color = ForgeColors.Danger)
                        }
                    }
                }

                Text("Proyek baru", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nama proyek") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = withStarter, onCheckedChange = { withStarter = it })
                    Text("  Isi dengan contoh platformer", color = ForgeColors.TextMuted)
                }
                Button(
                    onClick = { vm.newProject(newName, withStarter); onDismiss() },
                    enabled = newName.isNotBlank()
                ) { Text("Buat proyek") }
            }
        }
    )
}

@Composable
private fun SettingsDialog(vm: EditorViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(vm.project.name) }
    var pkg by remember { mutableStateOf(vm.project.packageId) }
    var width by remember { mutableStateOf(vm.project.settings.designWidth.toInt().toString()) }
    var height by remember { mutableStateOf(vm.project.settings.designHeight.toInt().toString()) }
    var gravity by remember { mutableStateOf(vm.project.settings.gravity.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                vm.renameProject(name)
                vm.setPackageId(pkg)
                vm.updateSettings { settings ->
                    settings.copy(
                        designWidth = width.toFloatOrNull() ?: settings.designWidth,
                        designHeight = height.toFloatOrNull() ?: settings.designHeight,
                        gravity = gravity.toFloatOrNull() ?: settings.gravity
                    )
                }
                onDismiss()
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        title = { Text("Pengaturan game") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama game") }, singleLine = true)
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it },
                    label = { Text("Package id (untuk APK)") },
                    supportingText = { Text("contoh: com.namaku.gameku") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = width,
                        onValueChange = { width = it },
                        label = { Text("Lebar") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Tinggi") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = gravity,
                    onValueChange = { gravity = it },
                    label = { Text("Gravitasi (px/detik²)") },
                    singleLine = true
                )
                SwitchRow("Mode landscape", vm.project.settings.landscape) { on ->
                    vm.updateSettings { it.copy(landscape = on) }
                }
                SwitchRow("Tampilkan tombol virtual", vm.project.settings.showVirtualPad) { on ->
                    vm.updateSettings { it.copy(showVirtualPad = on) }
                }
                SwitchRow("Tampilkan FPS", vm.project.settings.showFps) { on ->
                    vm.updateSettings { it.copy(showFps = on) }
                }
            }
        }
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = ForgeColors.TextPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Thin wrapper so previews and the activity share one entry point. */
@Composable
fun EditorRoot(vm: EditorViewModel) {
    Box(Modifier.fillMaxSize().background(ForgeColors.Canvas)) { EditorScreen(vm) }
}
