import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.voicechoicer.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.voicechoicer.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Offline speech recognition needs a Whisper (ggml) model bundled as an asset - a single ~142MB
// binary file, fetched at build time (and cached under the user's home directory across
// builds/CI runs) rather than committed to the repository - see .gitignore for the resulting
// src/main/assets/whisper-model/ directory. "base" (not "base.en") is the multilingual checkpoint,
// needed for Polish.
val whisperModelFileName = "ggml-base.bin"
val whisperModelUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$whisperModelFileName"
val whisperModelAssetsDir = layout.projectDirectory.dir("src/main/assets/whisper-model")

val downloadWhisperModel = tasks.register("downloadWhisperModel") {
    description = "Downloads and stages the offline multilingual Whisper speech-recognition model into app assets."
    val assetsDir = whisperModelAssetsDir.asFile
    val destFile = File(assetsDir, whisperModelFileName)
    outputs.file(destFile)
    onlyIf { !destFile.exists() }

    doLast {
        assetsDir.mkdirs()
        val cacheDir = File(System.getProperty("user.home"), ".whisper-model-cache")
        cacheDir.mkdirs()
        val cachedFile = File(cacheDir, whisperModelFileName)
        if (!cachedFile.exists()) {
            logger.lifecycle("Downloading Whisper model from $whisperModelUrl ...")
            URI(whisperModelUrl).toURL().openStream().use { input ->
                cachedFile.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            logger.lifecycle("Using cached Whisper model at $cachedFile")
        }
        cachedFile.copyTo(destFile, overwrite = true)
    }
}

tasks.named("preBuild") {
    dependsOn(downloadWhisperModel)
}

dependencies {
    implementation(project(":core"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.whisper.android)

    testImplementation(libs.junit)
}
