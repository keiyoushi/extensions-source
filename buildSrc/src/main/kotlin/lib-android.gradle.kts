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
