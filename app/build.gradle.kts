import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The MVB OAuth client the backend expects (the official app's). Not in the
// repository: from the environment (CI: repository secrets) or from a
// gitignored secrets.properties next to settings.gradle.kts.
val secrets = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() } ?: secrets.getProperty(name)
val mvbClientId = secret("MVB_CLIENT_ID")
val mvbClientSecret = secret("MVB_CLIENT_SECRET")

android {
    namespace = "com.succ.antitrack.magdeburg"
    compileSdk = 35
    buildToolsVersion = "35.0.0" // the one baked into build/Containerfile

    defaultConfig {
        applicationId = "com.succ.antitrack.magdeburg"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
        buildConfigField("String", "MVB_CLIENT_ID", "\"${mvbClientId.orEmpty()}\"")
        buildConfigField("String", "MVB_CLIENT_SECRET", "\"${mvbClientSecret.orEmpty()}\"")
    }

    // Release signing from the environment only: the keystore path and its
    // passwords never sit in the tree or in argv. Unset, a release build is
    // unsigned.
    // CI passes unset secrets as empty strings: treat those as absent.
    fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
    val keystore = env("ANTITRACK_KEYSTORE")
    if (keystore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystore)
                storePassword = env("ANTITRACK_KEYSTORE_PASSWORD")
                keyAlias = env("ANTITRACK_KEY_ALIAS") ?: "antitrack"
                keyPassword = env("ANTITRACK_KEY_PASSWORD") ?: env("ANTITRACK_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
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
        compose = true
        buildConfig = true
    }
    // No dependency-info block in the APK: it is only read by Google Play.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
}

// A release without the client can't log anyone in: refuse to build one.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        if (mvbClientId.isNullOrBlank() || mvbClientSecret.isNullOrBlank()) {
            throw GradleException("MVB_CLIENT_ID / MVB_CLIENT_SECRET missing (env or secrets.properties)")
        }
    }
}
