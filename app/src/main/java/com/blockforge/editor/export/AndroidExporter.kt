package com.blockforge.editor.export

import android.content.Context
import android.content.res.AssetManager
import com.blockforge.engine.model.GameProject
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a complete, buildable Android Studio project as a zip.
 *
 * The export is not a wrapper around the editor — it is a standalone Gradle project containing the
 * engine runtime as Kotlin source, the game as `assets/game.json`, and the sprites and audio the
 * game uses. Unzip it, open it in Android Studio (or push it to GitHub and let the bundled Actions
 * workflow do it), and you get an APK.
 *
 * Text files come from [ProjectTemplates]; this class only adds the parts that need Android — the
 * staged engine sources, the Gradle wrapper, and the project's own media.
 */
object AndroidExporter {

    private const val ENGINE_ASSET_ROOT = "engine_src"
    private const val WRAPPER_ASSET_ROOT = "wrapper"

    /** File name suggested to the system file picker. */
    fun suggestedFileName(project: GameProject): String = ProjectTemplates.suggestedFileName(project)

    fun export(context: Context, project: GameProject, resDir: File, out: OutputStream): ExportReport {
        val assets = context.assets
        val root = ProjectTemplates.rootFolderName(project)
        val report = ExportReport()

        ZipOutputStream(out.buffered()).use { zip ->
            fun binary(path: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry("$root/$path"))
                zip.write(bytes)
                zip.closeEntry()
                report.files++
                report.bytes += bytes.size
            }

            ProjectTemplates.textFiles(project).forEach { (path, content) ->
                binary(path, content.toByteArray(Charsets.UTF_8))
            }

            copyAssetTree(assets, WRAPPER_ASSET_ROOT, report) { name, bytes ->
                when (name) {
                    "gradlew" -> binary("gradlew", bytes)
                    "gradlew.bat" -> binary("gradlew.bat", bytes)
                    "gradle-wrapper.jar" -> binary("gradle/wrapper/gradle-wrapper.jar", bytes)
                    "gradle-wrapper.properties" -> binary("gradle/wrapper/gradle-wrapper.properties", bytes)
                }
            }

            // The engine ships as source so the exported project has no private dependencies.
            copyAssetTree(assets, ENGINE_ASSET_ROOT, report) { relative, bytes ->
                binary("app/src/main/java/$relative", bytes)
                report.engineFiles++
            }

            project.assets.forEach { ref ->
                val source = File(resDir, ref.fileName)
                if (source.isFile) {
                    binary("app/src/main/assets/res/${ref.fileName}", source.readBytes())
                    report.assetFiles++
                } else {
                    report.missing += ref.fileName
                }
            }
        }
        return report
    }

    data class ExportReport(
        var files: Int = 0,
        var engineFiles: Int = 0,
        var assetFiles: Int = 0,
        var bytes: Long = 0,
        val missing: MutableList<String> = mutableListOf()
    ) {
        fun summary(): String = buildString {
            append("$files berkas ditulis · $engineFiles berkas engine · $assetFiles aset")
            if (missing.isNotEmpty()) append(" · ${missing.size} aset hilang: ${missing.joinToString()}")
        }
    }

    /** Recursively walks an asset folder. AssetManager has no walker, so this is the one we get. */
    private fun copyAssetTree(
        assets: AssetManager,
        root: String,
        report: ExportReport,
        emit: (relativePath: String, bytes: ByteArray) -> Unit
    ) {
        fun walk(path: String) {
            val children = runCatching { assets.list(path) }.getOrNull() ?: return
            if (children.isEmpty()) {
                val bytes = runCatching { assets.open(path).use { it.readBytes() } }.getOrNull()
                if (bytes == null) {
                    report.missing += path
                    return
                }
                emit(path.removePrefix("$root/"), bytes)
                return
            }
            children.forEach { child -> walk("$path/$child") }
        }
        walk(root)
    }
}
