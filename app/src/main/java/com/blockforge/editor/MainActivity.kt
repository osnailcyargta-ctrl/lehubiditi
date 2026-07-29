package com.blockforge.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blockforge.editor.ui.EditorRoot
import com.blockforge.editor.ui.theme.BlockForgeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BlockForgeTheme {
                val vm: EditorViewModel = viewModel(factory = EditorViewModel.factory(this))
                EditorRoot(vm)
            }
        }
    }
}
