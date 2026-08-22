package com.blockforge.editor.export

import com.blockforge.engine.model.ProjectIO
import com.blockforge.engine.model.StarterProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Materialises a real exported project on disk.
 *
 * The point is not the assertions — it is the directory this leaves behind. CI runs this and then
 * runs `./gradlew assembleDebug` inside the result, so "the export is buildable" is a fact the
 * build checks rather than a claim in the README.
 */
class ExportedProjectTest {

    private val repoRoot = File(System.getProperty("blockforge.repoRoot") ?: "..")
    private val outDir = File(System.getProperty("blockforge.exportDir") ?: "build/exported-sample")

    @Test
    fun writesABuildableProject() {
        val project = StarterProject.create("Contoh Ekspor")

        outDir.deleteRecursively()
        outDir.mkdirs()

        ProjectTemplates.textFiles(project).forEach { (path, content) ->
            val target = File(outDir, path)
            target.parentFile?.mkdirs()
            target.writeText(content)
        }

        val engineSource = File(repoRoot, "engine/src/main/java")
        assertTrue("engine sources not found at $engineSource", engineSource.isDirectory)
        val engineFiles = copyTree(engineSource, File(outDir, "app/src/main/java"))
        assertTrue("expected engine sources to be copied", engineFiles > 10)

        copyWrapper()

        // The runtime has to be able to read back exactly what the exporter wrote.
        val gameJson = File(outDir, "app/src/main/assets/game.json")
        assertTrue("game.json missing", gameJson.isFile)
        val roundTripped = ProjectIO.decode(gameJson.readText())
        assertEquals(project.name, roundTripped.name)
        assertEquals(project.scenes.size, roundTripped.scenes.size)
        assertEquals(
            project.scenes.sumOf { it.objects.size },
            roundTripped.scenes.sumOf { it.objects.size }
        )

        val activity = File(outDir, "app/src/main/java/${project.packageId.replace('.', '/')}/GameActivity.kt")
        assertTrue("GameActivity not written to its package path", activity.isFile)

        listOf(
            "settings.gradle.kts",
            "build.gradle.kts",
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml",
            "gradlew",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties"
        ).forEach { path ->
            assertTrue("missing $path", File(outDir, path).isFile)
        }
    }

    private fun copyTree(from: File, into: File): Int {
        var count = 0
        from.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val target = File(into, file.relativeTo(from).path)
            target.parentFile?.mkdirs()
            file.copyTo(target, overwrite = true)
            count++
        }
        return count
    }

    private fun copyWrapper() {
        val pairs = listOf(
            "gradlew" to "gradlew",
            "gradlew.bat" to "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.jar" to "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties" to "gradle/wrapper/gradle-wrapper.properties"
        )
        pairs.forEach { (source, dest) ->
            val src = File(repoRoot, source)
            if (!src.isFile) return@forEach
            val target = File(outDir, dest)
            target.parentFile?.mkdirs()
            src.copyTo(target, overwrite = true)
            // copyTo drops the permission bits, and Gradle refuses to run a non-executable wrapper.
            if (dest == "gradlew") target.setExecutable(true)
        }
    }
}
