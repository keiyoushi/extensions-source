plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdk = AndroidConfig.compileSdk

    defaultConfig {
        minSdk = AndroidConfig.minSdk
    }

    namespace = "eu.kanade.tachiyomi.lib.synchrony"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.bundles.common)
}
