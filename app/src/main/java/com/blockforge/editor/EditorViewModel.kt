package com.blockforge.editor

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.blockforge.editor.data.ProjectEntry
import com.blockforge.editor.data.ProjectStore
import com.blockforge.engine.blocks.BlockCatalog
import com.blockforge.engine.blocks.BlockTree
import com.blockforge.engine.model.Arg
import com.blockforge.engine.model.AssetKind
import com.blockforge.engine.model.AssetRef
import com.blockforge.engine.model.BlockNode
import com.blockforge.engine.model.GameObject
import com.blockforge.engine.model.GameProject
import com.blockforge.engine.model.GameSettings
import com.blockforge.engine.model.Scene
import com.blockforge.engine.model.VariableDef
import com.blockforge.engine.model.VariableKind
import com.blockforge.engine.model.VariableScope

enum class EditorTab(val title: String) {
    SCENE("Scene"), BLOCKS("Blok"), ASSETS("Aset"), PLAY("Main")
}

/** Where the next block from the palette will land. */
data class InsertionTarget(val laneId: String, val index: Int)

/** A slot the user tapped, waiting for a value or a reporter block. */
data class SlotTarget(val blockId: String, val slotKey: String)

class EditorViewModel(context: Context) : ViewModel() {

    val store = ProjectStore(context)

    var projectId: String by mutableStateOf("")
        private set
    var project: GameProject by mutableStateOf(GameProject())
        private set

    var sceneId: String by mutableStateOf("")
        private set
    var objectId: String? by mutableStateOf(null)
        private set

    var tab: EditorTab by mutableStateOf(EditorTab.BLOCKS)
    var selectedBlockId: String? by mutableStateOf(null)
    var insertion: InsertionTarget? by mutableStateOf(null)
    var slotTarget: SlotTarget? by mutableStateOf(null)
    var message: String? by mutableStateOf(null)

    /** Block ids executing right now, pushed from the running game for the live glow. */
    var activeBlocks: Set<String> by mutableStateOf(emptySet())

    private val undoStack = ArrayDeque<GameProject>()
    private val redoStack = ArrayDeque<GameProject>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    init {
        val (id, loaded) = store.openOrCreate()
        projectId = id
        project = loaded
        sceneId = loaded.startScene.id
        objectId = loaded.startScene.objects.lastOrNull()?.id
    }

    // ---- derived ------------------------------------------------------------------------------

    val scene: Scene get() = project.scene(sceneId) ?: project.scenes.first()
    val selectedObject: GameObject? get() = objectId?.let { id -> scene.objects.firstOrNull { it.id == id } }
    val scripts: List<BlockNode> get() = selectedObject?.scripts.orEmpty()

    fun projects(): List<ProjectEntry> = store.list()

    // ---- project lifecycle --------------------------------------------------------------------

    fun openProject(id: String) {
        val loaded = store.load(id) ?: return
        store.setLastOpened(id)
        projectId = id
        project = loaded
        sceneId = loaded.startScene.id
        objectId = loaded.startScene.objects.firstOrNull()?.id
        selectedBlockId = null
        insertion = null
        undoStack.clear()
        redoStack.clear()
        toast("Proyek '${loaded.name}' dibuka")
    }

    fun newProject(name: String, withStarter: Boolean) {
        val (id, created) = store.create(name.ifBlank { "Game Baru" }, withStarter)
        projectId = id
        project = created
        sceneId = created.startScene.id
        objectId = created.startScene.objects.firstOrNull()?.id
        selectedBlockId = null
        undoStack.clear()
        redoStack.clear()
        toast("Proyek baru dibuat")
    }

    fun deleteProject(id: String) {
        store.delete(id)
        if (id == projectId) {
            val (nextId, next) = store.openOrCreate()
            projectId = nextId
            project = next
            sceneId = next.startScene.id
            objectId = next.startScene.objects.firstOrNull()?.id
        }
        toast("Proyek dihapus")
    }

    // ---- mutation core ------------------------------------------------------------------------

    /**
     * Every edit funnels through here: one place that pushes undo, writes to disk, and clears redo.
     * [record] is false for continuous gestures so a drag is one undo step, not two hundred.
     */
    private fun mutate(record: Boolean = true, persist: Boolean = true, transform: (GameProject) -> GameProject) {
        val previous = project
        val next = transform(previous)
        if (next === previous) return
        if (record) {
            undoStack.addLast(previous)
            if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
            redoStack.clear()
        }
        project = next
        if (persist) store.save(projectId, next)
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(project)
        project = previous
        store.save(projectId, previous)
        reconcileSelection()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(project)
        project = next
        store.save(projectId, next)
        reconcileSelection()
    }

    private fun reconcileSelection() {
        if (project.scene(sceneId) == null) sceneId = project.startScene.id
        if (scene.objects.none { it.id == objectId }) objectId = scene.objects.firstOrNull()?.id
        if (selectedBlockId != null && BlockTree.findNode(scripts, selectedBlockId!!) == null) selectedBlockId = null
    }

    fun save() {
        store.save(projectId, project)
    }

    fun toast(text: String) {
        message = text
    }

    fun clearToast() {
        message = null
    }

    // ---- settings -----------------------------------------------------------------------------

    fun renameProject(name: String) = mutate { it.copy(name = name) }

    fun setPackageId(id: String) = mutate { it.copy(packageId = id) }

    fun updateSettings(transform: (GameSettings) -> GameSettings) =
        mutate { it.copy(settings = transform(it.settings)) }

    // ---- scenes -------------------------------------------------------------------------------

    fun selectScene(id: String) {
        sceneId = id
        objectId = project.scene(id)?.objects?.firstOrNull()?.id
        selectedBlockId = null
    }

    fun addScene(name: String) {
        val created = Scene(name = name.ifBlank { "Scene ${project.scenes.size + 1}" })
        mutate { it.copy(scenes = it.scenes + created) }
        selectScene(created.id)
        toast("Scene ditambahkan")
    }

    fun renameScene(id: String, name: String) = mutate { p ->
        p.copy(scenes = p.scenes.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun deleteScene(id: String) {
        if (project.scenes.size <= 1) {
            toast("Minimal satu scene harus ada"); return
        }
        mutate { p ->
            val remaining = p.scenes.filterNot { it.id == id }
            p.copy(scenes = remaining, startSceneId = if (p.startSceneId == id) remaining.first().id else p.startSceneId)
        }
        if (sceneId == id) selectScene(project.scenes.first().id)
    }

    fun setStartScene(id: String) = mutate { it.copy(startSceneId = id) }

    fun setSceneBackground(color: Int?, assetId: String?) = mutateScene { scene ->
        scene.copy(backgroundColor = color ?: scene.backgroundColor, backgroundAssetId = assetId)
    }

    private fun mutateScene(record: Boolean = true, persist: Boolean = true, transform: (Scene) -> Scene) =
        mutate(record, persist) { p ->
            p.copy(scenes = p.scenes.map { if (it.id == sceneId) transform(it) else it })
        }

    // ---- objects ------------------------------------------------------------------------------

    fun selectObject(id: String?) {
        objectId = id
        selectedBlockId = null
        insertion = null
    }

    fun addObject(name: String = "Objek ${scene.objects.size + 1}") {
        val settings = project.settings
        val created = GameObject(
            name = name,
            x = settings.designWidth / 2f,
            y = settings.designHeight / 2f,
            fallbackColor = OBJECT_COLORS[scene.objects.size % OBJECT_COLORS.size]
        )
        mutateScene { it.copy(objects = it.objects + created) }
        selectObject(created.id)
        toast("Objek '${created.name}' ditambahkan")
    }

    fun duplicateObject(id: String) {
        val source = scene.objects.firstOrNull { it.id == id } ?: return
        val copy = source.copy(
            id = com.blockforge.engine.model.newId("obj"),
            name = "${source.name} salinan",
            x = source.x + 40f,
            y = source.y + 40f,
            scripts = source.scripts.map { BlockTree.regenerateIds(it) }
        )
        mutateScene { it.copy(objects = it.objects + copy) }
        selectObject(copy.id)
    }

    fun deleteObject(id: String) {
        mutateScene { it.copy(objects = it.objects.filterNot { o -> o.id == id }) }
        if (objectId == id) selectObject(scene.objects.firstOrNull()?.id)
        toast("Objek dihapus")
    }

    fun updateObject(id: String, record: Boolean = true, persist: Boolean = true, transform: (GameObject) -> GameObject) =
        mutateScene(record, persist) { scene ->
            scene.copy(objects = scene.objects.map { if (it.id == id) transform(it) else it })
        }

    // ---- variables & messages -----------------------------------------------------------------

    fun addVariable(name: String, kind: VariableKind, scope: VariableScope, initial: String) {
        val created = VariableDef(
            name = name.ifBlank { "variabel" },
            kind = kind,
            scope = scope,
            initial = initial,
            showOnScreen = kind == VariableKind.NUMBER
        )
        mutate { it.copy(variables = it.variables + created) }
        toast("Variabel '${created.name}' dibuat")
    }

    fun updateVariable(id: String, transform: (VariableDef) -> VariableDef) = mutate { p ->
        p.copy(variables = p.variables.map { if (it.id == id) transform(it) else it })
    }

    fun deleteVariable(id: String) = mutate { p ->
        p.copy(variables = p.variables.filterNot { it.id == id })
    }

    fun addMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || project.messages.any { it.equals(clean, true) }) return
        mutate { it.copy(messages = it.messages + clean) }
    }

    // ---- assets -------------------------------------------------------------------------------

    fun importAsset(uri: Uri, kind: AssetKind) {
        val ref = store.importAsset(projectId, uri, kind, project.assets)
        if (ref == null) {
            toast("Gagal mengimpor berkas"); return
        }
        mutate { it.copy(assets = it.assets + ref) }
        toast("${if (kind == AssetKind.IMAGE) "Gambar" else "Suara"} '${ref.name}' ditambahkan")
    }

    fun renameAsset(id: String, name: String) = mutate { p ->
        p.copy(assets = p.assets.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun deleteAsset(id: String) {
        val ref = project.asset(id) ?: return
        store.deleteAssetFile(projectId, ref.fileName)
        mutate { p ->
            p.copy(
                assets = p.assets.filterNot { it.id == id },
                scenes = p.scenes.map { scene ->
                    scene.copy(
                        backgroundAssetId = scene.backgroundAssetId?.takeIf { it != id },
                        objects = scene.objects.map { obj ->
                            if (obj.spriteAssetId == id) obj.copy(spriteAssetId = null) else obj
                        }
                    )
                }
            )
        }
        toast("Aset dihapus")
    }

    fun assetFile(ref: AssetRef) = store.assetFile(projectId, ref.fileName)

    // ---- blocks -------------------------------------------------------------------------------

    private fun mutateScripts(record: Boolean = true, persist: Boolean = true, transform: (List<BlockNode>) -> List<BlockNode>) {
        val id = objectId ?: return
        updateObject(id, record, persist) { it.copy(scripts = transform(it.scripts)) }
    }

    /** Adds a hat block, which starts a brand-new script with its own main lane. */
    fun addScript(type: String, x: Float, y: Float) {
        val node = BlockCatalog.instantiate(type).copy(canvasX = x, canvasY = y)
        mutateScripts { it + node }
        selectedBlockId = node.id
        insertion = node.branch(0)?.let { InsertionTarget(it.id, 0) }
        toast("Skrip '${BlockCatalog.require(type).plainLabel}' dibuat")
    }

    /**
     * Inserts a block at the pending insertion point. The lane grows by exactly one row and every
     * lane below it shifts down automatically, because lane heights are computed, never stored.
     */
    fun addBlockAtInsertion(type: String) {
        val target = insertion
        val def = BlockCatalog.require(type)
        if (def.shape == com.blockforge.engine.blocks.BlockShape.HAT) {
            val bottom = scripts.maxOfOrNull { it.canvasY } ?: 0f
            addScript(type, 40f, if (scripts.isEmpty()) 40f else bottom + 260f)
            return
        }
        if (target == null) {
            toast("Pilih dulu titik sisip (tombol + pada lane)"); return
        }
        val node = BlockCatalog.instantiate(type)
        mutateScripts { BlockTree.insertIntoLane(it, target.laneId, target.index, node) }
        selectedBlockId = node.id
        // Keep inserting downward, so building a lane is one tap per block.
        insertion = InsertionTarget(target.laneId, target.index + 1)
    }

    fun deleteBlock(id: String) {
        mutateScripts { BlockTree.removeNode(it, id).first }
        if (selectedBlockId == id) selectedBlockId = null
        toast("Blok dihapus — lane menyusut satu baris")
    }

    fun duplicateBlock(id: String) {
        val node = BlockTree.findNode(scripts, id) ?: return
        val copy = BlockTree.regenerateIds(node)
        val laneId = BlockTree.parentLaneId(scripts, id)
        if (laneId == null) {
            mutateScripts { it + copy.copy(canvasX = node.canvasX + 60f, canvasY = node.canvasY + 60f) }
        } else {
            val index = (BlockTree.findLane(scripts, laneId)?.nodes?.indexOfFirst { it.id == id } ?: 0) + 1
            mutateScripts { BlockTree.insertIntoLane(it, laneId, index, copy) }
        }
        selectedBlockId = copy.id
    }

    fun moveBlock(blockId: String, laneId: String, index: Int) {
        mutateScripts { BlockTree.moveNode(it, blockId, laneId, index) }
        insertion = InsertionTarget(laneId, index + 1)
    }

    fun moveScript(id: String, x: Float, y: Float, record: Boolean) {
        mutateScripts(record = record, persist = record) { scripts ->
            scripts.map { if (it.id == id) it.copy(canvasX = x, canvasY = y) else it }
        }
    }

    fun setSlotLiteral(blockId: String, key: String, value: String) {
        mutateScripts { BlockTree.setArg(it, blockId, key, Arg.Lit(value)) }
    }

    fun setSlotBlock(blockId: String, key: String, type: String) {
        val reporter = BlockCatalog.instantiate(type)
        mutateScripts { BlockTree.setArg(it, blockId, key, Arg.Blk(reporter)) }
    }

    fun clearSlot(blockId: String, key: String) {
        mutateScripts { BlockTree.clearArg(it, blockId, key) }
    }

    fun addBranch(blockId: String) {
        mutateScripts { BlockTree.addBranch(it, blockId, "cabang ${(BlockTree.findNode(it, blockId)?.branches?.size ?: 0) + 1}") }
        toast("Cabang baru dibuka ke kanan")
    }

    fun removeBranch(blockId: String, index: Int) {
        mutateScripts { BlockTree.removeBranch(it, blockId, index) }
    }

    /** Wraps an existing block in a new control block, moving it into the control block's branch. */
    fun wrapInControl(blockId: String, controlType: String) {
        val laneId = BlockTree.parentLaneId(scripts, blockId) ?: run {
            toast("Blok kepala tidak bisa dibungkus"); return
        }
        val node = BlockTree.findNode(scripts, blockId) ?: return
        val index = BlockTree.findLane(scripts, laneId)?.nodes?.indexOfFirst { it.id == blockId } ?: return
        val wrapper = BlockCatalog.instantiate(controlType).let { control ->
            val lane = (control.branch(0) ?: com.blockforge.engine.model.Lane()).copy(nodes = listOf(node))
            if (control.branches.isEmpty()) control.copy(branches = listOf(lane)) else control.withBranch(0, lane)
        }
        mutateScripts { current ->
            val (without, _) = BlockTree.removeNode(current, blockId)
            BlockTree.insertIntoLane(without, laneId, index, wrapper)
        }
        selectedBlockId = wrapper.id
        toast("Dibungkus — isinya pindah ke cabang kanan")
    }

    fun blockCount(): Int = BlockTree.countBlocks(scripts)

    companion object {
        private const val MAX_UNDO = 60

        private val OBJECT_COLORS = intArrayOf(
            0xFF4FC3F7.toInt(), 0xFFFFD166.toInt(), 0xFFEF6F6C.toInt(),
            0xFF9BE8A6.toInt(), 0xFFA66BFF.toInt(), 0xFF22B8CF.toInt()
        )

        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EditorViewModel(context.applicationContext) as T
        }
    }
}
