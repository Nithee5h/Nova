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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures { viewBinding = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    androidResources {
        // optional; fine to remove if you never ship model blobs in APK
        noCompress += listOf("task", "tflite", "bin")
    }

    packaging {
        resources {
            excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

/* --- kill stray old junit/hamcrest pulled transitively on main classpath --- */
configurations.configureEach {
    exclude(group = "junit", module = "junit")
    exclude(group = "org.hamcrest", module = "hamcrest-core")
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")

    val camerax = "1.3.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.camera:camera-extensions:$camerax")

    implementation("com.alphacephei:vosk-android:0.3.38") {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.googlecode.json-simple:json-simple:1.1.1")

    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // MediaPipe GenAI (one line only; remove any other versions)
    implementation("com.google.mediapipe:tasks-genai:0.10.24")

    implementation("com.google.protobuf:protobuf-javalite:3.21.12")

    /* --- put JUnit ONLY on test classpath, modern version --- */
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
