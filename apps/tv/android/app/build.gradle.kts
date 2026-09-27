import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// ── Release signing ──────────────────────────────────────────────────────
// Mirrors the mobile app: env vars (CI) > android/key.properties (local).
// Falls back to the debug key when nothing is configured.
val keyProperties = Properties()
val keyPropertiesFile = rootProject.file("key.properties")
if (keyPropertiesFile.exists()) {
    keyPropertiesFile.inputStream().use { keyProperties.load(it) }
}

fun prop(name: String, env: String): String? {
    val fromEnv = System.getenv(env)
    if (!fromEnv.isNullOrBlank()) return fromEnv
    return keyProperties.getProperty(name)
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

val releaseStorePassword = prop("storePassword", "LS_STORE_PASSWORD")
val releaseKeyAlias = prop("keyAlias", "LS_KEY_ALIAS")
val releaseKeyPassword = prop("keyPassword", "LS_KEY_PASSWORD")

val releaseSigningAvailable = releaseKeystoreFile != null &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

android {
    namespace = "com.localstream.localstream_tv"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
        applicationId = "com.localstream.localstream_tv"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            signingConfig = if (releaseSigningAvailable) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

flutter {
    source = "../.."
}