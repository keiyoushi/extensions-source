plugins {
    id("com.android.library")
    kotlin("android")
    id("kotlinx-serialization")
    id("keiyoushi.lint")
}

android {
    compileSdk = AndroidConfig.compileSdk

    defaultConfig {
        minSdk = AndroidConfig.minSdk
    }

    namespace = "eu.kanade.tachiyomi.lib.${project.name}"

    buildFeatures {
        androidResources = false
    }

    kotlinOptions {
        freeCompilerArgs += "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(versionCatalogs.named("libs").findBundle("common").get())
}

tasks.register("printDependentExtensions") {
    doLast {
        project.printDependentExtensions()
    }
}
