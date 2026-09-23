plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.localstream.localstream_mobile"
    // Explicitly set SDK versions for Android 13-16 support
    compileSdk = 36
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        applicationId = "com.localstream.localstream_mobile"
        minSdk = 33   // Android 13 minimum
        targetSdk = 36 // Android 16 target
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        buildConfigField("String", "DEVICE_FEEDBACK_URL", "\"${project.findProperty("deviceFeedbackUrl") ?: ""}\"")
        buildConfigField("String", "DEVICE_FEEDBACK_TOKEN", "\"${project.findProperty("deviceFeedbackToken") ?: ""}\"")
    }

    buildTypes {
        release {
            // Signing with the debug keys for now.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
        }
        debug {
            isDebuggable = true
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Kotlin coroutines for async HTTP handling
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Lifecycle service for foreground service
    implementation("androidx.lifecycle:lifecycle-service:2.9.0")
    // Core KTX
    implementation("androidx.core:core-ktx:1.16.0")
    // DocumentFile for Storage Access Framework (SAF)
    implementation("androidx.documentfile:documentfile:1.0.1")
    // JSON (built-in, but explicit for clarity)
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
}

flutter {
    source = "../.."
}

