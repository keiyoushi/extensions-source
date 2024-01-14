import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdk = AndroidConfig.compileSdk

    defaultConfig {
        minSdk = AndroidConfig.minSdk
    }

    namespace = "eu.kanade.tachiyomi.lib.${name.removePrefix("lib-")}"
}

repositories {
    mavenCentral()
}

val libs = the<LibrariesForLibs>()

dependencies {
    compileOnly(libs.kotlin.stdlib)
}
