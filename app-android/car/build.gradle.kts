import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Stessa config di :app: URL e token Turso di SOLA LETTURA da local.properties (git-ignored)
// o env var -> BuildConfig. Non versionati. Vedi local.properties.example.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cfg(key: String, default: String = ""): String =
    localProps.getProperty(key) ?: System.getenv(key) ?: default

android {
    namespace = "com.dallariva.carburanti.car"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "TURSO_DATABASE_URL", "\"${cfg("TURSO_DATABASE_URL")}\"")
        buildConfigField("String", "TURSO_RO_TOKEN", "\"${cfg("TURSO_RO_TOKEN")}\"")
    }

    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":data"))

    // Car App Library (Android Auto / categoria POI)
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-projected:1.7.0")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
