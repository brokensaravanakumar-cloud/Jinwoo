// Versions below were chosen from a known-compatible set (AGP 8.5 / Kotlin 1.9.24 / Compose compiler 1.5.14).
// They could NOT be re-verified online from the build sandbox. If Android Studio offers an upgrade, accept it
// (and keep Kotlin, KSP and the Compose compiler versions matched).
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
