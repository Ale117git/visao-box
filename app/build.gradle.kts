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
        versionCode = 3
        versionName = "2.0"
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
        noCompress += listOf("task")
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

// Baixa o modelo de reconhecimento de mãos durante a compilação (fica dentro do app, funciona offline)
val modelFile = file("src/main/assets/hand_landmarker.task")
val downloadModel by tasks.registering {
    outputs.file(modelFile)
    doLast {
        if (!modelFile.exists() || modelFile.length() < 1000) {
            modelFile.parentFile.mkdirs()
            val url = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"
            uri(url).toURL().openStream().use { input -> modelFile.outputStream().use { input.copyTo(it) } }
            println("Modelo baixado: ${modelFile.length()} bytes")
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(downloadModel) }
