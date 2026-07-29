package com.blockforge.engine.model

import kotlinx.serialization.Serializable

/**
 * The full, serialisable description of a game. This is the single artifact the editor writes and
 * the runtime reads, so an exported project is nothing more than `game.json` + assets + the runtime.
 */
@Serializable
data class GameProject(
    val formatVersion: Int = 1,
    val name: String = "Game Baru",
    val packageId: String = "com.example.mygame",
    val versionName: String = "1.0",
    val versionCode: Int = 1,
    val settings: GameSettings = GameSettings(),
    val assets: List<AssetRef> = emptyList(),
    val variables: List<VariableDef> = emptyList(),
    val messages: List<String> = listOf("mulai"),
    val scenes: List<Scene> = listOf(Scene()),
    val startSceneId: String = scenes.firstOrNull()?.id ?: ""
) {
    fun scene(id: String): Scene? = scenes.firstOrNull { it.id == id }
    fun asset(id: String?): AssetRef? = id?.let { a -> assets.firstOrNull { it.id == a } }
    fun variable(id: String?): VariableDef? = id?.let { v -> variables.firstOrNull { it.id == v } }

    val startScene: Scene
        get() = scene(startSceneId) ?: scenes.first()
}

@Serializable
data class GameSettings(
    /** Virtual resolution. The renderer letterboxes this into whatever the device actually has. */
    val designWidth: Float = 960f,
    val designHeight: Float = 540f,
    val landscape: Boolean = true,
    val backgroundColor: Int = 0xFF0E1116.toInt(),
    /** Downward acceleration in px/s^2 applied to bodies with physics enabled. */
    val gravity: Float = 1400f,
    val showVirtualPad: Boolean = true,
    val showFps: Boolean = false,
    val pixelArt: Boolean = true
)

@Serializable
data class Scene(
    val id: String = newId("scene"),
    val name: String = "Scene Utama",
    val backgroundColor: Int? = null,
    val backgroundAssetId: String? = null,
    val objects: List<GameObject> = emptyList()
) {
    fun obj(id: String?): GameObject? = id?.let { o -> objects.firstOrNull { it.id == o } }
}

@Serializable
data class GameObject(
    val id: String = newId("obj"),
    val name: String = "Objek",
    val tag: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 96f,
    val height: Float = 96f,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val alpha: Float = 1f,
    val zIndex: Int = 0,
    val visible: Boolean = true,
    val spriteAssetId: String? = null,
    /** Used when no sprite is assigned — an object is always visible on screen, never a mystery. */
    val fallbackColor: Int = 0xFF4FC3F7.toInt(),
    val shape: ObjectShape = ObjectShape.RECT,
    val physics: PhysicsBody = PhysicsBody(),
    /** Top-level scripts. Each entry is a hat block owning one downward lane. */
    val scripts: List<BlockNode> = emptyList()
)

@Serializable
enum class ObjectShape { RECT, CIRCLE }

@Serializable
data class PhysicsBody(
    val enabled: Boolean = false,
    /** Static bodies never move but still collide — floors, walls, platforms. */
    val static: Boolean = false,
    val gravityScale: Float = 1f,
    val bounce: Float = 0f,
    /** Per-second horizontal damping, 0 = ice, 1 = instant stop. */
    val friction: Float = 0f,
    val solid: Boolean = true
)

@Serializable
data class AssetRef(
    val id: String = newId("asset"),
    val name: String = "asset",
    val kind: AssetKind = AssetKind.IMAGE,
    /** File name relative to the project's `res/` folder (and to `assets/res/` once exported). */
    val fileName: String = ""
)

@Serializable
enum class AssetKind { IMAGE, AUDIO }

@Serializable
data class VariableDef(
    val id: String = newId("var"),
    val name: String = "skor",
    val kind: VariableKind = VariableKind.NUMBER,
    val initial: String = "0",
    val scope: VariableScope = VariableScope.GLOBAL,
    /** Draws a live readout in the corner during play. */
    val showOnScreen: Boolean = false
)

@Serializable
enum class VariableKind { NUMBER, TEXT, BOOLEAN }

@Serializable
enum class VariableScope {
    /** One shared slot for the whole game. */
    GLOBAL,

    /** Every actor gets its own copy — useful for per-enemy health. */
    OBJECT
}

private var idCounter = 0L

/** Short, readable, collision-free ids. Readability matters when reading a diff of `game.json`. */
fun newId(prefix: String): String {
    idCounter += 1
    val stamp = (System.nanoTime() and 0xFFFFFF).toString(36)
    return "${prefix}_${stamp}${idCounter.toString(36)}"
}
