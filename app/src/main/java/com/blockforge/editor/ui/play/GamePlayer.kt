package com.blockforge.editor.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.blockforge.editor.EditorViewModel
import com.blockforge.editor.ui.theme.ForgeColors
import com.blockforge.editor.ui.theme.ForgeIcons
import com.blockforge.engine.GameView
import com.blockforge.engine.model.GameProject
import com.blockforge.engine.runtime.FileResourceProvider
import com.blockforge.engine.runtime.GameHost
import java.io.File

/**
 * Embeds the real runtime inside the editor.
 *
 * This is the same [GameView] an exported APK uses, so "Main" is not a simulation of the game — it
 * *is* the game, running against the project as it stands right now.
 */
@Composable
fun GamePlayer(
    project: GameProject,
    resDir: File,
    /** Bump to reload the project from scratch. */
    reloadToken: Int,
    paused: Boolean,
    onActiveBlocks: (Set<String>) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var lastActive by remember { mutableStateOf<Set<String>>(emptySet()) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            GameView(context).apply {
                host = object : GameHost {
                    override fun onError(message: String) {
                        post { onError(message) }
                    }
                }
                onFrame = { ids ->
                    // Runs on the game thread; hop to the UI thread and only when the set changes,
                    // otherwise the editor would recompose sixty times a second for nothing.
                    if (ids != lastActive) {
                        lastActive = ids
                        post { onActiveBlocks(ids) }
                    }
                }
            }
        },
        update = { view ->
            val tag = view.getTag(TOKEN_TAG) as? Int
            if (tag != reloadToken) {
                view.setTag(TOKEN_TAG, reloadToken)
                view.load(project, FileResourceProvider(resDir))
            }
            view.setPaused(paused)
        },
        onRelease = { view -> view.release() }
    )
}

private const val TOKEN_TAG = 0x7f5a0001

/** Full-screen Play tab with transport controls. */
@Composable
fun PlayPanel(vm: EditorViewModel, modifier: Modifier = Modifier) {
    var reload by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }

    // Reload whenever the tab is opened so edits made elsewhere are always reflected.
    DisposableEffect(Unit) {
        reload++
        onDispose { vm.activeBlocks = emptySet() }
    }

    Column(modifier.fillMaxSize().background(ForgeColors.Canvas)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().background(ForgeColors.Panel).padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                vm.project.name,
                style = MaterialTheme.typography.titleMedium,
                color = ForgeColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { paused = !paused }) {
                Icon(
                    if (paused) Icons.Default.PlayArrow else ForgeIcons.Pause,
                    contentDescription = if (paused) "Lanjutkan" else "Jeda",
                    tint = ForgeColors.Accent
                )
            }
            IconButton(onClick = { reload++; paused = false }) {
                Icon(Icons.Default.Refresh, contentDescription = "Mulai ulang", tint = ForgeColors.Accent)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            GamePlayer(
                project = vm.project,
                resDir = vm.store.resDir(vm.projectId),
                reloadToken = reload,
                paused = paused,
                onActiveBlocks = { vm.activeBlocks = it },
                onError = { vm.toast(it) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
