import java.net.URI
import java.util.zip.ZipInputStream

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

// Offline speech recognition needs a Vosk acoustic model bundled as an asset. The model is a
// ~50MB binary blob, so it is fetched at build time (and cached under the user's home directory
// across builds/CI runs) rather than committed to the repository - see .gitignore for the
// resulting src/main/assets/model-pl-small/ directory.
val voskModelVersion = "0.22"
val voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-pl-$voskModelVersion.zip"
val voskModelAssetsDir = layout.projectDirectory.dir("src/main/assets/model-pl-small")

val downloadVoskModel = tasks.register("downloadVoskModel") {
    description = "Downloads and stages the offline Polish speech-recognition model into app assets."
    val assetsDir = voskModelAssetsDir.asFile
    // Kept outside assetsDir on purpose: Vosk's Model loader walks that whole directory expecting
    // only the model's own files, and we don't want to risk it tripping over an extra stray file.
    val markerFile = layout.buildDirectory.file("vosk-model-version.txt").get().asFile
    outputs.dir(assetsDir)
    onlyIf { !markerFile.exists() || markerFile.readText().trim() != voskModelVersion }

    doLast {
        if (assetsDir.exists()) assetsDir.deleteRecursively()
        assetsDir.mkdirs()

        val cacheDir = File(System.getProperty("user.home"), ".vosk-model-cache")
        cacheDir.mkdirs()
        val zipFile = File(cacheDir, "vosk-model-small-pl-$voskModelVersion.zip")
        if (!zipFile.exists()) {
            logger.lifecycle("Downloading Vosk Polish model from $voskModelUrl ...")
            URI(voskModelUrl).toURL().openStream().use { input ->
                zipFile.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            logger.lifecycle("Using cached Vosk model archive at $zipFile")
        }

        logger.lifecycle("Unpacking Vosk model into $assetsDir ...")
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // Zip entries are "vosk-model-small-pl-<version>/<rest>"; drop that top-level dir.
                val relativePath = entry.name.substringAfter('/', missingDelimiterValue = "")
                if (relativePath.isNotEmpty() && !entry.isDirectory) {
                    val outFile = File(assetsDir, relativePath)
                    outFile.parentFile.mkdirs()
                    outFile.outputStream().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        markerFile.parentFile.mkdirs()
        markerFile.writeText(voskModelVersion)
    }
}

tasks.named("preBuild") {
    dependsOn(downloadVoskModel)
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

    implementation(libs.vosk.android)

    testImplementation(libs.junit)
}
