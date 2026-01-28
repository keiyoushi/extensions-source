plugins {
    id("com.android.library")
    id("kotlinx-serialization")
}

android {
    compileSdk = AndroidConfig.compileSdk

    defaultConfig {
        minSdk = AndroidConfig.minSdk
    }

    namespace = "eu.kanade.tachiyomi.multisrc.${project.name}"

    sourceSets {
        named("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.directories.clear()
            java.directories.add("src")
            kotlin.directories.clear()
            kotlin.directories.add("src")
            res.directories.clear()
            res.directories.add("res")
            assets.directories.clear()
            assets.directories.add("assets")
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
    }
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
