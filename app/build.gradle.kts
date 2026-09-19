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

        // sherpa-onnx's native library is only fetched for arm64-v8a (see downloadSherpaNativeLibs)
        // to keep the APK small - matches real devices and the existing Whisper setup.
        ndk {
            abiFilters += "arm64-v8a"
        }
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

// Real ML-based speaker diarization (pyannote segmentation + a wespeaker speaker-embedding
// model, clustered by sherpa-onnx) needs sherpa's JNI native library - there is no Maven
// artifact for Android, so the .so and the Kotlin API wrapper (committed under
// app/src/main/kotlin/com/k2fsa/sherpa/onnx/) are the only ways to use it - plus two small
// ONNX models. All fetched at build time and cached locally / in CI rather than committed -
// see .gitignore for the resulting src/main/jniLibs/arm64-v8a/ and
// src/main/assets/diarization-model/ directories, and SherpaDiarizer for how they're used.
val sherpaVersion = "1.12.14"
val sherpaNativeLibsUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-v$sherpaVersion-android.tar.bz2"
val pyannoteSegmentationUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2"
val speakerEmbeddingUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/wespeaker_en_voxceleb_resnet34.onnx"
val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs")
val diarizationModelsDir = layout.projectDirectory.dir("src/main/assets/diarization-model")

fun sherpaOnnxCacheDir(): File {
    val cacheDir = File(System.getProperty("user.home"), ".sherpa-onnx-cache")
    cacheDir.mkdirs()
    return cacheDir
}

val downloadSherpaNativeLibs = tasks.register("downloadSherpaNativeLibs") {
    description = "Downloads the sherpa-onnx JNI native library (arm64-v8a only; no Maven artifact exists for Android)."
    val destFile = File(jniLibsDir.asFile, "arm64-v8a/libsherpa-onnx-jni.so")
    outputs.file(destFile)
    onlyIf { !destFile.exists() }

    doLast {
        val cacheDir = sherpaOnnxCacheDir()
        val cachedArchive = File(cacheDir, "sherpa-onnx-v$sherpaVersion-android.tar.bz2")
        if (!cachedArchive.exists()) {
            logger.lifecycle("Downloading sherpa-onnx native libraries from $sherpaNativeLibsUrl ...")
            URI(sherpaNativeLibsUrl).toURL().openStream().use { input ->
                cachedArchive.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            logger.lifecycle("Using cached sherpa-onnx archive at $cachedArchive")
        }
        copy {
            from(tarTree(resources.bzip2(cachedArchive)))
            include("jniLibs/arm64-v8a/**")
            into(jniLibsDir.asFile)
            eachFile { path = path.removePrefix("jniLibs/") }
            includeEmptyDirs = false
        }
    }
}

val downloadDiarizationModels = tasks.register("downloadDiarizationModels") {
    description = "Downloads the pyannote speaker-segmentation and wespeaker speaker-embedding models used for real ML-based diarization."
    val assetsDir = diarizationModelsDir.asFile
    val segmentationFile = File(assetsDir, "segmentation.onnx")
    val embeddingFile = File(assetsDir, "embedding.onnx")
    outputs.files(segmentationFile, embeddingFile)
    onlyIf { !segmentationFile.exists() || !embeddingFile.exists() }

    doLast {
        assetsDir.mkdirs()
        val cacheDir = sherpaOnnxCacheDir()

        if (!segmentationFile.exists()) {
            val cachedArchive = File(cacheDir, "sherpa-onnx-pyannote-segmentation-3-0.tar.bz2")
            if (!cachedArchive.exists()) {
                logger.lifecycle("Downloading pyannote segmentation model from $pyannoteSegmentationUrl ...")
                URI(pyannoteSegmentationUrl).toURL().openStream().use { input ->
                    cachedArchive.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                logger.lifecycle("Using cached pyannote segmentation archive at $cachedArchive")
            }
            val cachedModelFile = File(cacheDir, "pyannote-segmentation.int8.onnx")
            if (!cachedModelFile.exists()) {
                copy {
                    from(tarTree(resources.bzip2(cachedArchive)))
                    include("sherpa-onnx-pyannote-segmentation-3-0/model.int8.onnx")
                    into(cacheDir)
                    eachFile { path = "pyannote-segmentation.int8.onnx" }
                    includeEmptyDirs = false
                }
            }
            cachedModelFile.copyTo(segmentationFile, overwrite = true)
        }

        if (!embeddingFile.exists()) {
            val cachedFile = File(cacheDir, "wespeaker_en_voxceleb_resnet34.onnx")
            if (!cachedFile.exists()) {
                logger.lifecycle("Downloading speaker embedding model from $speakerEmbeddingUrl ...")
                URI(speakerEmbeddingUrl).toURL().openStream().use { input ->
                    cachedFile.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                logger.lifecycle("Using cached speaker embedding model at $cachedFile")
            }
            cachedFile.copyTo(embeddingFile, overwrite = true)
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadWhisperModel, downloadSherpaNativeLibs, downloadDiarizationModels)
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
