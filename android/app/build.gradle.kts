// One version for the whole project: server/package.json (the server, the web app and this APK).
val appVersion = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(rootProject.file("../server/package.json").readText())!!.groupValues[1]
val (vMajor, vMinor, vPatch) = appVersion.split(".").map { it.toInt() }
val releaseKeystore: String? = System.getenv("SONORA_KEYSTORE") // set by the GitHub release workflow

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "it.casa.sonora"
    compileSdk = 36
    defaultConfig {
        applicationId = "it.casa.sonora"
        minSdk = 26
        targetSdk = 36
        versionCode = vMajor * 10000 + vMinor * 100 + vPatch
        versionName = appVersion
    }
    signingConfigs {
        create("release") {
            if (releaseKeystore != null) {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("SONORA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SONORA_KEY_ALIAS") ?: "sonora"
                keyPassword = System.getenv("SONORA_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Releases are signed with the project key (CI secret): every update installs over the previous one.
            // Local builds without the key fall back to the debug key, fine for testing.
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
}
