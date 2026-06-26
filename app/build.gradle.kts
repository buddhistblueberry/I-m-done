plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python") version "15.0.1"  // ← Added
}

android {
    namespace = "com.mariocart.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mariocart.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.1.1"

        buildConfigField("String", "TMDB_API_KEY", "\"a15c24c2a5c00487b179f5d4b53b72b0\"")

        ndk {
            abiFilters("armeabi-v7a", "arm64-v8a")  // Reduce APK size
        }
    }

    // ... keep the rest of your android {} block (buildTypes, buildFeatures, etc.)

    python {
        version "3.11"
        pip {
            install("requests")
        }
    }
}

// Keep your existing dependencies
dependencies {
    // ... your existing dependencies
}
