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
        androidResources = false
    }
}

dependencies {
    compileOnly(versionCatalogs.named("libs").findBundle("common").get())
}

tasks.register("getDependents") {
    doLast {
        project.getDependents().forEach {
            if (it.path.startsWith(":src:")) {
                println(it.path)
            } else if (it.path.startsWith(":lib-multisrc:")) {
                it.getDependents().forEach {
                    println(it.path)
                }
            } else if (it.path.startsWith(":lib:")) {
                it.getDependents().forEach {
                    if (it.path.startsWith(":src:")) {
                        println(it.path)
                    } else if (it.path.startsWith(":lib-multisrc:")) {
                        it.getDependents().forEach {
                            println(it.path)
                        }
                    }
                }
            }
        }
    }
}

tasks.register("assembleDebugAll") {
    val dependents = project.getDependents().mapNotNull { dependent ->
        if (dependent.path == project.path) {
            null
        } else if (dependent.path.startsWith(":src:")) {
            "${dependent.path}:assembleDebug"
        } else if (dependent.path.startsWith(":lib")) {
            "${dependent.path}:assembleDebugAll"
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
    val dependents = project.getDependents().mapNotNull { dependent ->
        if (dependent.path == project.path) {
            null
        } else if (dependent.path.startsWith(":src:")) {
            "${dependent.path}:assembleRelease"
        } else if (dependent.path.startsWith(":lib")) {
            "${dependent.path}:assembleReleaseAll"
        } else {
            null
        }
    }
    setFinalizedBy(dependents)
    doLast {
        println("running:\n${dependents.joinToString("\n")}")
    }
}
