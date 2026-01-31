plugins {
    id("com.android.library")
    id("kotlinx-serialization")
}

android {
    compileSdk = AndroidConfig.compileSdk

    defaultConfig {
        minSdk = AndroidConfig.minSdk
    }

    namespace = "eu.kanade.tachiyomi.lib.${project.name}"

    androidResources.enable = false
}

dependencies {
    implementation(versionCatalogs.named("libs").findBundle("common-impl").get())
    compileOnly(versionCatalogs.named("libs").findBundle("common-compile").get())
    implementation(project(":core"))
}

tasks.register("printDependentExtensions") {
    doLast {
        project.printDependentExtensions()
    }
}
