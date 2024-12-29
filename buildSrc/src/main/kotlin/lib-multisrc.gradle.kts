plugins {
    id("com.android.library")
    kotlin("android")
    id("kotlinx-serialization")
    id("org.jmailen.kotlinter")
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
            java.setSrcDirs(listOf("src"))
            res.setSrcDirs(listOf("res"))
            assets.setSrcDirs(listOf("assets"))
        }
    }

    kotlinOptions {
        freeCompilerArgs += "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    }
}

kotlinter {
    experimentalRules = true
    disabledRules = arrayOf(
        "experimental:argument-list-wrapping", // Doesn't play well with Android Studio
        "experimental:comment-wrapping",
    )
}

dependencies {
    compileOnly(versionCatalogs.named("libs").findBundle("common").get())
}

tasks {
    preBuild {
        dependsOn(lintKotlin)
    }

    if (System.getenv("CI") != "true") {
        lintKotlin {
            dependsOn(formatKotlin)
        }
    }
}

tasks.register("getDependents") {
    doLast {
        project.getDependents().mapNotNull { project ->
            if (project.path.startsWith(":src:")) {
                println(project.path)
            } else {
                null
            }
        }
    }
}

tasks.register("assembleDebugAll") {
    val dependents = project.getDependents().mapNotNull { project ->
        if (project.path.startsWith(":src:")) {
            "${project.path}:assembleDebug"
        } else {
            null
        }
    }
    setFinalizedBy(dependents)
    doLast {
        println("running:\n${dependents.joinToString("\n")}")
    }
}

tasks.register("assembleReleaseAll") {
    val dependents = project.getDependents().mapNotNull { project ->
        if (project.path.startsWith(":src:")) {
            "${project.path}:assembleRelease"
        } else {
            null
        }
    }
    setFinalizedBy(dependents)
    doLast {
        println("running:\n${dependents.joinToString("\n")}")
    }
}
