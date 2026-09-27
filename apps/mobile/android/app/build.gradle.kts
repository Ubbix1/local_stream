import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// ── Release signing ──────────────────────────────────────────────────────
// Sources of truth, in priority order:
//   1. Environment variables (CI): LS_KEYSTORE_BASE64 | LS_KEYSTORE_PATH,
//      LS_STORE_PASSWORD, LS_KEY_ALIAS, LS_KEY_PASSWORD
//   2. android/key.properties (local): storeFile, storePassword,
//      keyAlias, keyPassword
// When no credentials are configured, release builds fall back to the debug
// key so development APKs keep building everywhere.
val keyProperties = Properties()
val keyPropertiesFile = rootProject.file("key.properties")
if (keyPropertiesFile.exists()) {
    keyPropertiesFile.inputStream().use { keyProperties.load(it) }
}

fun local(props: Properties, name: String, env: String): String? {
    val fromEnv = System.getenv(env)
    if (!fromEnv.isNullOrBlank()) return fromEnv
    return props.getProperty(name)
}

val envStoreB64 = System.getenv("LS_KEYSTORE_BASE64")
val envStorePath = System.getenv("LS_KEYSTORE_PATH")

val releaseKeystoreFile: File? = when {
    !envStoreB64.isNullOrBlank() -> {
        val decoded = Base64.getDecoder().decode(envStoreB64)
        val target = rootProject.file("release-keystore.jks")
        target.parentFile?.mkdirs()
        target.writeBytes(decoded)
        target
    }

    !envStorePath.isNullOrBlank() && File(envStorePath).exists() -> File(envStorePath)

    keyProperties.getProperty("storeFile") != null -> rootProject.file(keyProperties.getProperty("storeFile").trim())

    else -> null
}

val releaseStorePassword = local(keyProperties, "storePassword", "LS_STORE_PASSWORD")
val releaseKeyAlias = local(keyProperties, "keyAlias", "LS_KEY_ALIAS")
val releaseKeyPassword = local(keyProperties, "keyPassword", "LS_KEY_PASSWORD")

val releaseSigningAvailable = releaseKeystoreFile != null &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

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

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
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
            signingConfig = if (releaseSigningAvailable) {
                signingConfigs.getByName("release")
            } else {
                // Fall back to debug keys when no release keystore is configured.
                signingConfigs.getByName("debug")
            }
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