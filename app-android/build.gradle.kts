// Root build file: dichiara i plugin Android/Kotlin a livello root (apply false),
// cosi' i moduli figli li applicano senza ripetere la versione.
// Il modulo :data resta un puro Kotlin/JVM (vedi data/build.gradle.kts) ed e' usabile
// come dipendenza da :app (modulo Android) senza conversioni.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.24" apply false
    kotlin("plugin.serialization") version "1.9.24" apply false
}
