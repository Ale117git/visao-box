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
        versionCode = 15
        versionName = "5.1"
        // só os processadores de celular (deixa o APK bem menor)
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
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
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    // assistente Visão: reconhece "ei visão" sem internet
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
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

// Modelo de voz em português do assistente Visão (~31 MB). Se o download falhar, o app compila mesmo assim
// e o assistente funciona só pelo botão do microfone.
val downloadVosk by tasks.registering {
    val dir = file("src/main/assets/vosk-pt")
    val zip = File(layout.buildDirectory.get().asFile, "vosk-pt.zip")
    doLast {
        if (File(dir, "am").exists()) return@doLast
        try {
            zip.parentFile.mkdirs()
            uri("https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip").toURL().openStream()
                .use { input -> zip.outputStream().use { input.copyTo(it) } }
            project.copy {
                from(project.zipTree(zip))
                into(dir)
                includeEmptyDirs = false
                eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
            }
            println("Modelo de voz baixado em ${dir}")
        } catch (e: Exception) {
            println("AVISO: não consegui baixar o modelo de voz do assistente: ${e.message}")
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(downloadModels, downloadVosk) }
