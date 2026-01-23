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
}

androidComponents.onVariants { variant ->
    variant.sources.manifests.addStaticManifestFile("AndroidManifest.xml")
    variant.sources.java!!.addStaticSourceDirectory("src")
    variant.sources.res!!.addStaticSourceDirectory("res")
    variant.sources.assets!!.addStaticSourceDirectory("assets")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
    }
}

//kotlinter {
//    experimentalRules = true
//    disabledRules = arrayOf(
//        "experimental:argument-list-wrapping", // Doesn't play well with Android Studio
//        "experimental:comment-wrapping",
//    )
//}

dependencies {
    compileOnly(versionCatalogs.named("libs").findBundle("common").get())
    implementation(project(":core"))
}

//tasks {
//    preBuild {
//        dependsOn(lintKotlin)
//    }
//
//    if (System.getenv("CI") != "true") {
//        lintKotlin {
//            dependsOn(formatKotlin)
//        }
//    }
//}

tasks.register("printDependentExtensions") {
    doLast {
        project.printDependentExtensions()
    }
}
