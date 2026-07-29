package com.blockforge.editor.export

import android.content.Context
import android.content.res.AssetManager
import com.blockforge.engine.model.GameProject
import com.blockforge.engine.model.ProjectIO
import java.io.File
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a complete, buildable Android Studio project as a zip.
 *
 * The export is not a wrapper around the editor — it is a standalone Gradle project containing the
 * engine runtime as Kotlin source, the game as `assets/game.json`, and the sprites and audio the
 * game uses. Unzip it, open it in Android Studio (or push it to GitHub and let the bundled Actions
 * workflow do it), and you get an APK.
 */
object AndroidExporter {

    private const val ENGINE_ASSET_ROOT = "engine_src"
    private const val WRAPPER_ASSET_ROOT = "wrapper"

    /** File name suggested to the system file picker. */
    fun suggestedFileName(project: GameProject): String =
        slug(project.name) + "-android-project.zip"

    fun export(context: Context, project: GameProject, resDir: File, out: OutputStream): ExportReport {
        val assets = context.assets
        val root = slug(project.name).ifEmpty { "game" }
        val pkgPath = project.packageId.replace('.', '/')
        val report = ExportReport()

        ZipOutputStream(out.buffered()).use { zip ->
            fun text(path: String, content: String) {
                zip.putNextEntry(ZipEntry("$root/$path"))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                report.files++
            }

            fun binary(path: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry("$root/$path"))
                zip.write(bytes)
                zip.closeEntry()
                report.files++
                report.bytes += bytes.size
            }

            // ---- gradle shell ----
            text("settings.gradle.kts", settingsGradle(project.name))
            text("build.gradle.kts", rootBuildGradle())
            text("gradle.properties", gradleProperties())
            text("app/build.gradle.kts", appBuildGradle(project))
            text("app/proguard-rules.pro", proguardRules())

            copyAssetTree(assets, WRAPPER_ASSET_ROOT, report) { name, bytes ->
                when (name) {
                    "gradlew" -> binary("gradlew", bytes)
                    "gradlew.bat" -> binary("gradlew.bat", bytes)
                    "gradle-wrapper.jar" -> binary("gradle/wrapper/gradle-wrapper.jar", bytes)
                    "gradle-wrapper.properties" -> binary("gradle/wrapper/gradle-wrapper.properties", bytes)
                }
            }

            // ---- app sources ----
            text("app/src/main/AndroidManifest.xml", manifest(project))
            text("app/src/main/java/$pkgPath/GameActivity.kt", gameActivity(project))
            text("app/src/main/res/values/strings.xml", strings(project))
            text("app/src/main/res/drawable/ic_launcher.xml", launcherIcon(project))

            // ---- engine runtime, shipped as source so the project has no private dependencies ----
            copyAssetTree(assets, ENGINE_ASSET_ROOT, report) { relative, bytes ->
                binary("app/src/main/java/$relative", bytes)
                report.engineFiles++
            }

            // ---- the game itself ----
            text("app/src/main/assets/game.json", ProjectIO.encode(project, pretty = false))
            project.assets.forEach { ref ->
                val source = File(resDir, ref.fileName)
                if (source.isFile) {
                    binary("app/src/main/assets/res/${ref.fileName}", source.readBytes())
                    report.assetFiles++
                } else {
                    report.missing += ref.fileName
                }
            }

            // ---- convenience ----
            text(".github/workflows/build-apk.yml", ciWorkflow())
            text(".gitignore", gitignore())
            text("README.md", readme(project))
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

    // ---- asset walking ------------------------------------------------------------------------

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

    private fun slug(text: String): String {
        val cleaned = text.lowercase(Locale.US).map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("").trim('-').replace(Regex("-+"), "-")
        return cleaned.ifEmpty { "game" }
    }

    // ---- file templates -----------------------------------------------------------------------

    private fun settingsGradle(name: String) = """
        pluginManagement {
            repositories {
                google {
                    content {
                        includeGroupByRegex("com\\.android.*")
                        includeGroupByRegex("com\\.google.*")
                        includeGroupByRegex("androidx.*")
                    }
                }
                mavenCentral()
                gradlePluginPortal()
            }
        }

        dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                google()
                mavenCentral()
            }
        }

        rootProject.name = "${escape(name)}"
        include(":app")
    """.trimIndent()

    private fun rootBuildGradle() = """
        plugins {
            id("com.android.application") version "$AGP_VERSION" apply false
            id("org.jetbrains.kotlin.android") version "$KOTLIN_VERSION" apply false
            id("org.jetbrains.kotlin.plugin.serialization") version "$KOTLIN_VERSION" apply false
        }
    """.trimIndent()

    private fun gradleProperties() = """
        org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
        org.gradle.parallel=true
        android.useAndroidX=true
        android.nonTransitiveRClass=true
        kotlin.code.style=official
    """.trimIndent()

    private fun appBuildGradle(project: GameProject) = """
        plugins {
            id("com.android.application")
            id("org.jetbrains.kotlin.android")
            id("org.jetbrains.kotlin.plugin.serialization")
        }

        android {
            namespace = "${project.packageId}"
            compileSdk = 35

            defaultConfig {
                applicationId = "${project.packageId}"
                minSdk = 24
                targetSdk = 35
                versionCode = ${project.versionCode}
                versionName = "${escape(project.versionName)}"
            }

            buildTypes {
                release {
                    isMinifyEnabled = false
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                }
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            kotlinOptions { jvmTarget = "17" }
        }

        dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$SERIALIZATION_VERSION")
        }
    """.trimIndent()

    private fun proguardRules() = """
        # The project model is reconstructed from game.json by name, so keep it intact.
        -keep class com.blockforge.engine.model.** { *; }
        -keepclassmembers class com.blockforge.engine.model.** {
            kotlinx.serialization.KSerializer serializer(...);
        }
    """.trimIndent()

    private fun manifest(project: GameProject): String {
        val orientation = if (project.settings.landscape) "sensorLandscape" else "portrait"
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">

                <application
                    android:allowBackup="true"
                    android:icon="@drawable/ic_launcher"
                    android:label="@string/app_name"
                    android:supportsRtl="true"
                    android:hardwareAccelerated="true"
                    android:theme="@android:style/Theme.Material.NoTitleBar.Fullscreen">

                    <activity
                        android:name=".GameActivity"
                        android:exported="true"
                        android:screenOrientation="$orientation"
                        android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|density"
                        android:launchMode="singleTask">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent()
    }

    private fun gameActivity(project: GameProject) = """
        package ${project.packageId}

        import android.app.Activity
        import android.os.Build
        import android.os.Bundle
        import android.view.View
        import android.view.WindowManager
        import com.blockforge.engine.GameView
        import com.blockforge.engine.model.ProjectIO
        import com.blockforge.engine.runtime.AssetResourceProvider
        import com.blockforge.engine.runtime.GameHost

        /**
         * Generated by BlockForge 2D. The game itself lives in assets/game.json — edit the blocks in
         * the editor and re-export rather than hand-editing this file.
         */
        class GameActivity : Activity() {

            private lateinit var gameView: GameView

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                gameView = GameView(this)
                gameView.host = object : GameHost {
                    override fun onQuit() = runOnUiThread { finish() }
                }
                setContentView(gameView)

                val json = assets.open("game.json").bufferedReader().use { it.readText() }
                val project = ProjectIO.decode(json)
                gameView.load(project, AssetResourceProvider(assets, "res"))
            }

            override fun onWindowFocusChanged(hasFocus: Boolean) {
                super.onWindowFocusChanged(hasFocus)
                if (hasFocus) goFullscreen()
            }

            override fun onResume() {
                super.onResume()
                gameView.setPaused(false)
                gameView.requestFocus()
            }

            override fun onPause() {
                gameView.setPaused(true)
                super.onPause()
            }

            override fun onDestroy() {
                gameView.release()
                super.onDestroy()
            }

            @Suppress("DEPRECATION")
            private fun goFullscreen() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
                } else {
                    window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        )
                }
            }
        }
    """.trimIndent()

    private fun strings(project: GameProject) = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <string name="app_name">${escapeXml(project.name)}</string>
        </resources>
    """.trimIndent()

    private fun launcherIcon(project: GameProject): String {
        val hex = String.format("#%06X", project.settings.backgroundColor and 0xFFFFFF)
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp"
                android:height="108dp"
                android:viewportWidth="108"
                android:viewportHeight="108">
                <path
                    android:fillColor="$hex"
                    android:pathData="M0,0h108v108h-108z" />
                <path
                    android:fillColor="#4FC3F7"
                    android:pathData="M24,30h30v14h-30z" />
                <path
                    android:fillColor="#F2861D"
                    android:pathData="M40,50h30v14h-30z" />
                <path
                    android:fillColor="#3FB950"
                    android:pathData="M56,70h30v14h-30z" />
            </vector>
        """.trimIndent()
    }

    private fun ciWorkflow() = """
        name: Build APK

        on:
          push:
            branches: [ main, master ]
          workflow_dispatch:

        jobs:
          build:
            runs-on: ubuntu-latest
            steps:
              - uses: actions/checkout@v4

              - name: Set up JDK 17
                uses: actions/setup-java@v4
                with:
                  distribution: temurin
                  java-version: '17'

              - name: Set up Gradle
                uses: gradle/actions/setup-gradle@v4

              - name: Build debug APK
                run: ./gradlew assembleDebug --stacktrace

              - name: Upload APK
                uses: actions/upload-artifact@v4
                with:
                  name: game-debug-apk
                  path: app/build/outputs/apk/debug/*.apk
    """.trimIndent()

    private fun gitignore() = """
        .gradle/
        build/
        local.properties
        .idea/
        *.iml
        .DS_Store
    """.trimIndent()

    private fun readme(project: GameProject): String {
        val objects = project.scenes.sumOf { it.objects.size }
        return """
            # ${project.name}

            Proyek Android yang dihasilkan oleh **BlockForge 2D**.

            - Paket aplikasi: `${project.packageId}`
            - Scene: ${project.scenes.size} · Objek: $objects · Aset: ${project.assets.size}
            - Resolusi desain: ${project.settings.designWidth.toInt()} × ${project.settings.designHeight.toInt()}

            ## Cara build

            ### Android Studio
            1. Ekstrak folder ini.
            2. **File → Open**, pilih foldernya, tunggu Gradle sync.
            3. Klik **Run**.

            ### Baris perintah
            ```bash
            ./gradlew assembleDebug
            # APK: app/build/outputs/apk/debug/app-debug.apk
            ```

            ### GitHub
            Push repo ini ke GitHub. Workflow `.github/workflows/build-apk.yml` akan otomatis
            membangun APK setiap kali ada push, dan APK-nya bisa diunduh dari tab **Actions**.

            ## Isi proyek

            | Lokasi | Isi |
            |---|---|
            | `app/src/main/assets/game.json` | Seluruh game: scene, objek, dan skrip blok |
            | `app/src/main/assets/res/` | Sprite dan berkas audio |
            | `app/src/main/java/com/blockforge/engine/` | Runtime engine (interpreter, fisika, renderer) |
            | `app/src/main/java/${project.packageId.replace('.', '/')}/GameActivity.kt` | Titik masuk aplikasi |

            Untuk mengubah game, edit di BlockForge 2D lalu ekspor ulang.
        """.trimIndent()
    }

    private fun escape(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun escapeXml(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private const val AGP_VERSION = "8.7.3"
    private const val KOTLIN_VERSION = "2.0.21"
    private const val SERIALIZATION_VERSION = "1.7.3"
}
