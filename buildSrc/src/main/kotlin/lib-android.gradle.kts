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

    namespace = "eu.kanade.tachiyomi.lib.${project.name}"

    buildFeatures {
        resValues = false
        shaders = false
    }
}

// TODO: use versionCatalogs.named("libs") in Gradle 8.5
val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    compileOnly(libs.findBundle("common").get())
}
