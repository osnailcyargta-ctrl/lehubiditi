package com.blockforge.editor.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.blockforge.engine.model.AssetKind
import com.blockforge.engine.model.AssetRef
import com.blockforge.engine.model.GameProject
import com.blockforge.engine.model.ProjectIO
import com.blockforge.engine.model.StarterProject
import java.io.File
import java.util.Locale

data class ProjectEntry(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val sceneCount: Int,
    val objectCount: Int
)

/**
 * On-disk layout for projects:
 *
 * ```
 * filesDir/projects/<id>/game.json
 * filesDir/projects/<id>/res/<sprites and audio>
 * ```
 *
 * That `res/` folder is copied verbatim into `assets/res/` on export, so a sprite path never
 * changes between the editor and the built APK.
 */
class ProjectStore(private val context: Context) {

    private val root: File = File(context.filesDir, "projects").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("blockforge", Context.MODE_PRIVATE)

    fun dir(id: String): File = File(root, id).apply { mkdirs() }

    fun resDir(id: String): File = File(dir(id), "res").apply { mkdirs() }

    private fun file(id: String): File = File(dir(id), "game.json")

    fun list(): List<ProjectEntry> =
        root.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { folder ->
            val json = File(folder, "game.json")
            if (!json.isFile) return@mapNotNull null
            val project = ProjectIO.decodeOrNull(json.readText()) ?: return@mapNotNull null
            ProjectEntry(
                id = folder.name,
                name = project.name,
                updatedAt = json.lastModified(),
                sceneCount = project.scenes.size,
                objectCount = project.scenes.sumOf { it.objects.size }
            )
        }.sortedByDescending { it.updatedAt }

    fun create(name: String, starter: Boolean = true): Pair<String, GameProject> {
        val id = slug(name) + "_" + System.currentTimeMillis().toString(36)
        val project = if (starter) StarterProject.create(name) else GameProject(name = name)
        save(id, project)
        setLastOpened(id)
        return id to project
    }

    fun load(id: String): GameProject? {
        val f = file(id)
        if (!f.isFile) return null
        return ProjectIO.decodeOrNull(f.readText())
    }

    fun save(id: String, project: GameProject) {
        // Write-then-rename: a crash mid-save leaves the previous good project, never a half file.
        val target = file(id)
        val tmp = File(target.parentFile, "game.json.tmp")
        tmp.writeText(ProjectIO.encode(project))
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }

    fun delete(id: String) {
        dir(id).deleteRecursively()
        if (lastOpened() == id) prefs.edit().remove(KEY_LAST).apply()
    }

    fun lastOpened(): String? = prefs.getString(KEY_LAST, null)?.takeIf { File(root, it).isDirectory }

    fun setLastOpened(id: String) {
        prefs.edit().putString(KEY_LAST, id).apply()
    }

    /** Opens the last project, or seeds a starter one on first run so the app is never empty. */
    fun openOrCreate(): Pair<String, GameProject> {
        lastOpened()?.let { id -> load(id)?.let { return id to it } }
        list().firstOrNull()?.let { entry -> load(entry.id)?.let { setLastOpened(entry.id); return entry.id to it } }
        return create("Petualangan Pertama")
    }

    // ---- assets -------------------------------------------------------------------------------

    /** Copies a picked image/audio into the project and returns the reference to register. */
    fun importAsset(projectId: String, uri: Uri, kind: AssetKind, existing: List<AssetRef>): AssetRef? {
        val display = queryName(uri) ?: "asset"
        val extension = display.substringAfterLast('.', "").lowercase(Locale.US)
            .ifEmpty { if (kind == AssetKind.IMAGE) "png" else "mp3" }
        val base = slug(display.substringBeforeLast('.', display))
        val fileName = uniqueName(projectId, base, extension, existing)

        val target = File(resDir(projectId), fileName)
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!copied || !target.isFile || target.length() == 0L) {
            target.delete()
            return null
        }
        return AssetRef(name = display.substringBeforeLast('.', display), kind = kind, fileName = fileName)
    }

    fun deleteAssetFile(projectId: String, fileName: String) {
        File(resDir(projectId), fileName).delete()
    }

    fun assetFile(projectId: String, fileName: String): File = File(resDir(projectId), fileName)

    private fun uniqueName(projectId: String, base: String, ext: String, existing: List<AssetRef>): String {
        val taken = existing.map { it.fileName }.toMutableSet()
        resDir(projectId).listFiles()?.forEach { taken.add(it.name) }
        var candidate = "$base.$ext"
        var n = 2
        while (candidate in taken) {
            candidate = "${base}_$n.$ext"
            n++
        }
        return candidate
    }

    private fun queryName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return uri.lastPathSegment
    }

    private fun slug(text: String): String {
        val cleaned = text.lowercase(Locale.US).map { c ->
            if (c.isLetterOrDigit()) c else '_'
        }.joinToString("").trim('_')
        return cleaned.ifEmpty { "proyek" }.take(40)
    }

    private companion object {
        const val KEY_LAST = "last_project"
    }
}
