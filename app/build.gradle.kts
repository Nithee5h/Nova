plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.nova"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.nova"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // If you hit emulator ABI issues with native libs, you can force ARM APKs:
        // ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // keep default
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // Optional but useful to avoid accidental test libs in APK
    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }
}

configurations {
    // keep junit/hamcrest out of runtime
    configureEach {
        if (name.endsWith("RuntimeClasspath", ignoreCase = true)) {
            exclude(group = "junit")
            exclude(group = "org.hamcrest", module = "hamcrest-core")
        }
    }
    all {
        resolutionStrategy.eachDependency {
            if (requested.group == "junit" && requested.name == "junit") {
                useVersion("4.13.2")
            }
            if (requested.group == "org.hamcrest" && requested.name == "hamcrest-core") {
                useVersion("1.3")
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // CameraX
    val camerax = "1.3.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.camera:camera-extensions:$camerax")

    // Vosk for Android — exclude its transitive JNA JAR to prevent duplicate classes
    implementation("com.alphacephei:vosk-android:0.3.38") {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    // ✅ JNA as AAR so native libjnidispatch.so is packaged (do NOT also add the plain JAR)
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    // JSON-simple (used by MyVoskService)
    implementation("com.googlecode.json-simple:json-simple:1.1.1")

    // TensorFlow Lite Task Vision (object detection)
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.0")

    // Networking + coroutines for LLM streaming
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
