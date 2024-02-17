plugins {
    id("com.android.library")
    kotlin("android")
    id("kotlinx-serialization")
}

android {
    compileSdk = AndroidConfig.compileSdk

    defaultConfig {
        minSdk = AndroidConfig.minSdk
    }

    namespace = "eu.kanade.tachiyomi.lib.speedbinb"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.bundles.common)
    implementation(project(":lib:textinterceptor"))
}
