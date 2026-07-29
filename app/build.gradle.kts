plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.blockforge.editor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.blockforge.editor"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }
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
    buildFeatures { compose = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

/**
 * The exporter ships a *buildable* Android Studio project, which means the engine runtime has to
 * travel with it as source. These tasks stage the engine sources and the Gradle wrapper into the
 * editor APK's assets so [com.blockforge.editor.export.AndroidExporter] can write them back out.
 */
val stageEngineSources by tasks.registering(Copy::class) {
    from(rootProject.file("engine/src/main/java"))
    into(layout.projectDirectory.dir("src/main/assets/engine_src"))
    include("**/*.kt")
}

val stageWrapper by tasks.registering(Copy::class) {
    from(rootProject.file("gradle/wrapper/gradle-wrapper.jar"))
    from(rootProject.file("gradle/wrapper/gradle-wrapper.properties"))
    from(rootProject.file("gradlew"))
    from(rootProject.file("gradlew.bat"))
    into(layout.projectDirectory.dir("src/main/assets/wrapper"))
}

tasks.named("preBuild") {
    dependsOn(stageEngineSources, stageWrapper)
}

// The staged files land inside src/main/assets, so the asset merge has to wait for them.
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(stageEngineSources, stageWrapper)
    }
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.ui.tooling)
}
