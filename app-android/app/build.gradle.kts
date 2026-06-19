import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Configurazione letta da local.properties (git-ignored) o, in fallback, da env var.
// Il token Turso di SOLA LETTURA viene incorporato nell'APK via BuildConfig ma NON e'
// mai versionato nel sorgente. Vedi local.properties.example per il template.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cfg(key: String, default: String = ""): String =
    localProps.getProperty(key) ?: System.getenv(key) ?: default

android {
    namespace = "com.dallariva.carburanti.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dallariva.carburanti"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"

        // Endpoint e token di sola lettura -> BuildConfig (non hardcoded nel sorgente).
        buildConfigField("String", "TURSO_DATABASE_URL", "\"${cfg("TURSO_DATABASE_URL")}\"")
        buildConfigField("String", "TURSO_RO_TOKEN", "\"${cfg("TURSO_RO_TOKEN")}\"")
        // Mappa: di default uno stile raster costruito dai tile OpenStreetMap (niente API key).
        // Per usare uno stile vettoriale completo (es. MapTiler con chiave), valorizza MAP_STYLE_URL:
        // se non vuoto ha la precedenza su MAP_TILES_URL.
        buildConfigField(
            "String", "MAP_TILES_URL",
            "\"${cfg("MAP_TILES_URL", "https://tile.openstreetmap.org/{z}/{x}/{y}.png")}\""
        )
        buildConfigField("String", "MAP_STYLE_URL", "\"${cfg("MAP_STYLE_URL")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Posizione
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Mappa MapLibre + plugin annotazioni (marker colorati per prezzo)
    implementation("org.maplibre.gl:android-sdk:11.5.0")
    implementation("org.maplibre.gl:android-plugin-annotation-v9:3.0.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
