plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Optional wake-word engine: drop the sherpa-onnx Android AAR at app/libs/sherpa-onnx.aar (see docs/WAKE_WORD_SETUP.md).
// When present, the engine source set is compiled in. When absent, the app builds and honestly reports "engine missing".
val sherpaAar = file("libs/sherpa-onnx.aar")

android {
    namespace = "com.jinwoo.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jinwoo.ai"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEFAULT_BACKEND_URL", "\"http://10.0.2.2:8000\"") // emulator -> host PC
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "DEFAULT_BACKEND_URL", "\"https://your-backend.example.com\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    sourceSets {
        getByName("main") {
            java.srcDir("src/main/kotlin")
            if (sherpaAar.exists()) java.srcDir("src/sherpa/kotlin")
        }
        getByName("test") { java.srcDir("src/test/kotlin") }
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    if (sherpaAar.exists()) implementation(files("libs/sherpa-onnx.aar"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
