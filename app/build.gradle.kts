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

tasks.named("preBuild").configure { dependsOn(downloadSceneEmbeddingModel) }

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
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
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

    buildTypes {
        debug {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        release {
            isMinifyEnabled = true
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

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    implementation(libs.timber)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    debugImplementation(libs.compose.ui.tooling)
}
