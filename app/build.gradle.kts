plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI (see .github/workflows/release-debug-apk.yml) decodes a keystore from
// GitHub Secrets and points this env var at it, so every debug build is
// signed with the same key and can be installed over the previous one.
// Locally, this env var is simply unset and Android Gradle Plugin's normal
// auto-generated debug keystore is used instead — no setup required.
val ciKeystorePath: String? = System.getenv("YPVM_DEBUG_KEYSTORE")

android {
    namespace = "com.mnmyounus.ypvm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mnmyounus.ypvm"
        minSdk = 28   // Android 9: DPC-initiated Lock Task Mode needs API 28+
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    if (ciKeystorePath != null) {
        signingConfigs {
            getByName("debug") {
                storeFile = file(ciKeystorePath)
                storePassword = System.getenv("YPVM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("YPVM_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("YPVM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.lifecycle:lifecycle-service:2.8.3")
}
