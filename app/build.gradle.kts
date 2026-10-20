plugins {
    id("com.android.application")
}

android {
    namespace = "br.visaobox.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.visaobox.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 11
        versionName = "4.4"
    }

    signingConfigs {
        getByName("debug") {
            // chave fixa: assim as próximas versões instalam por cima, sem desinstalar
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += listOf("task", "tflite")
    }
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.22"))
    implementation("androidx.activity:activity:1.9.2")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("com.google.guava:guava:33.2.1-android")
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
}

// Baixa os modelos de visão computacional durante a compilação (ficam dentro do app e funcionam offline)
val models = mapOf(
    "hand_landmarker.task" to "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task",
    "selfie_segmenter.tflite" to "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite"
)
val downloadModels by tasks.registering {
    val dir = file("src/main/assets")
    doLast {
        dir.mkdirs()
        models.forEach { (name, url) ->
            val f = File(dir, name)
            if (!f.exists() || f.length() < 1000) {
                uri(url).toURL().openStream().use { input -> f.outputStream().use { input.copyTo(it) } }
                println("Modelo baixado: $name (${f.length()} bytes)")
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(downloadModels) }
