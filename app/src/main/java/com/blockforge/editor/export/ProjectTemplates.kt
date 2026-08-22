package com.blockforge.editor.export

import com.blockforge.engine.model.GameProject
import com.blockforge.engine.model.ProjectIO
import java.util.Locale

/**
 * Every text file that goes into an exported Android project.
 *
 * Kept free of Android APIs on purpose: [AndroidExporter] streams these into a zip on a device, and
 * a JVM unit test writes the same map to disk so CI can compile the result. One source of truth
 * means a template that stops compiling fails the build instead of shipping.
 */
object ProjectTemplates {

    const val AGP_VERSION = "8.7.3"
    const val KOTLIN_VERSION = "2.0.21"
    const val SERIALIZATION_VERSION = "1.7.3"

    /** Where the engine runtime lands inside the exported project. */
    const val ENGINE_PACKAGE_PATH = "app/src/main/java/com/blockforge/engine"

    /** Path → content for every text file in the export, in write order. */
    fun textFiles(project: GameProject): LinkedHashMap<String, String> {
        val pkgPath = project.packageId.replace('.', '/')
        return linkedMapOf(
            "settings.gradle.kts" to settingsGradle(project.name),
            "build.gradle.kts" to rootBuildGradle(),
            "gradle.properties" to gradleProperties(),
            "app/build.gradle.kts" to appBuildGradle(project),
            "app/proguard-rules.pro" to proguardRules(),
            "app/src/main/AndroidManifest.xml" to manifest(project),
            "app/src/main/java/$pkgPath/GameActivity.kt" to gameActivity(project),
            "app/src/main/res/values/strings.xml" to strings(project),
            "app/src/main/res/drawable/ic_launcher.xml" to launcherIcon(project),
            "app/src/main/assets/game.json" to ProjectIO.encode(project, pretty = false),
            ".github/workflows/build-apk.yml" to ciWorkflow(),
            ".gitignore" to gitignore(),
            "README.md" to readme(project)
        )
    }

    /** Folder name for the project inside the zip, and the suggested zip file name. */
    fun rootFolderName(project: GameProject): String = slug(project.name).ifEmpty { "game" }

    fun suggestedFileName(project: GameProject): String = "${rootFolderName(project)}-android-project.zip"

    fun slug(text: String): String {
        val cleaned = text.lowercase(Locale.US).map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("").trim('-').replace(Regex("-+"), "-")
        return cleaned.ifEmpty { "game" }
    }

    // ---- templates ----------------------------------------------------------------------------

    fun settingsGradle(name: String) = """
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

    fun rootBuildGradle() = """
        plugins {
            id("com.android.application") version "$AGP_VERSION" apply false
            id("org.jetbrains.kotlin.android") version "$KOTLIN_VERSION" apply false
            id("org.jetbrains.kotlin.plugin.serialization") version "$KOTLIN_VERSION" apply false
        }
    """.trimIndent()

    fun gradleProperties() = """
        org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
        org.gradle.parallel=true
        android.useAndroidX=true
        android.nonTransitiveRClass=true
        kotlin.code.style=official
    """.trimIndent()

    fun appBuildGradle(project: GameProject) = """
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

    fun proguardRules() = """
        # The project model is reconstructed from game.json by name, so keep it intact.
        -keep class com.blockforge.engine.model.** { *; }
        -keepclassmembers class com.blockforge.engine.model.** {
            kotlinx.serialization.KSerializer serializer(...);
        }
    """.trimIndent()

    fun manifest(project: GameProject): String {
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
                    android:theme="@android:style/Theme.Material.NoActionBar.Fullscreen">

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

    fun gameActivity(project: GameProject) = """
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
                    override fun onQuit() {
                        runOnUiThread { finish() }
                    }
                }
                setContentView(gameView)

                val json = assets.open("game.json").bufferedReader().use { it.readText() }
                gameView.load(ProjectIO.decode(json), AssetResourceProvider(assets, "res"))
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

    fun strings(project: GameProject) = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <string name="app_name">${escapeXml(project.name)}</string>
        </resources>
    """.trimIndent()

    fun launcherIcon(project: GameProject): String {
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

    fun ciWorkflow() = """
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

    fun gitignore() = """
        .gradle/
        build/
        local.properties
        .idea/
        *.iml
        .DS_Store
    """.trimIndent()

    fun readme(project: GameProject): String {
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
}
