import java.net.URL
import java.net.URI
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val releaseSigningValues = listOf(
    "AURA_RELEASE_STORE_FILE",
    "AURA_RELEASE_STORE_PASSWORD",
    "AURA_RELEASE_KEY_ALIAS",
    "AURA_RELEASE_KEY_PASSWORD",
).associateWith { System.getenv(it)?.takeIf(String::isNotBlank) }
val releaseSigningConfigured = releaseSigningValues.values.all { it != null }

val sceneEmbeddingModel = layout.projectDirectory.file("src/main/assets/mobilenet_v3_small.tflite")
val sceneEmbeddingModelSha256 = "bbbb4c51a55a53905af1daec995ca1aae355046f8839bb8c9f5ce9271394bc40"
val downloadSceneEmbeddingModel by tasks.registering {
    outputs.file(sceneEmbeddingModel)
    outputs.upToDateWhen {
        val file = sceneEmbeddingModel.asFile
        file.isFile && file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte) } == sceneEmbeddingModelSha256
        }
    }
    doLast {
        val output = sceneEmbeddingModel.asFile
        output.parentFile.mkdirs()
        if (!output.isFile || !outputs.files.singleFile.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { byte -> "%02x".format(byte) } == sceneEmbeddingModelSha256
            }) {
            val temporary = output.resolveSibling(output.name + ".download")
            try {
                URI.create("https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/1/mobilenet_v3_small.tflite").toURL()
                    .openConnection().apply { connectTimeout = 30_000; readTimeout = 60_000 }
                    .getInputStream().use { input -> temporary.outputStream().use(input::copyTo) }
                val digest = MessageDigest.getInstance("SHA-256")
                temporary.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                check(actual == sceneEmbeddingModelSha256) { "Scene embedding model checksum mismatch: $actual" }
                Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
        }
    }
}

val faceEmbeddingModel = layout.projectDirectory.file("src/main/assets/sface_2021dec_int8.onnx")
val faceEmbeddingModelSha256 = "2b0e941e6f16cc048c20aee0c8e31f569118f65d702914540f7bfdc14048d78a"
val downloadFaceEmbeddingModel by tasks.registering {
    outputs.file(faceEmbeddingModel)
    outputs.upToDateWhen {
        val file = faceEmbeddingModel.asFile
        file.isFile && file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte) } == faceEmbeddingModelSha256
        }
    }
    doLast {
        val output = faceEmbeddingModel.asFile
        output.parentFile.mkdirs()
        val temporary = output.resolveSibling(output.name + ".download")
        try {
            URI.create("https://huggingface.co/opencv/opencv_zoo/resolve/main/models/face_recognition_sface/face_recognition_sface_2021dec_int8.onnx")
                .toURL().openConnection().apply { connectTimeout = 30_000; readTimeout = 120_000 }
                .getInputStream().use { input -> temporary.outputStream().use(input::copyTo) }
            val digest = MessageDigest.getInstance("SHA-256")
            temporary.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            check(actual == faceEmbeddingModelSha256) { "SFace model checksum mismatch: $actual" }
            Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }
}

tasks.named("preBuild").configure { dependsOn(downloadSceneEmbeddingModel, downloadFaceEmbeddingModel) }

android {
    namespace = "ai.sakana.aura.gallery"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.sakana.aura.gallery"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseSigningValues.getValue("AURA_RELEASE_STORE_FILE")!!)
                storeType = System.getenv("AURA_RELEASE_STORE_TYPE")?.takeIf(String::isNotBlank) ?: "PKCS12"
                storePassword = releaseSigningValues.getValue("AURA_RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningValues.getValue("AURA_RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningValues.getValue("AURA_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

// Room KAPT exports one shared schema file for all Android variants. Keep the
// release processor after debug so parallel Gradle execution cannot read the
// schema while the other variant is replacing it.
tasks.matching { it.name == "kaptReleaseKotlin" }.configureEach {
    mustRunAfter("kaptDebugKotlin")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.core.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.mlkit.face.detection)
    implementation(libs.onnxruntime.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    implementation(libs.timber)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)
    implementation(libs.androidx.security.crypto)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    debugImplementation(libs.compose.ui.tooling)
}
