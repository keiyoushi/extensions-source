plugins {
    id("keiyoushi.android.library")
    id("keiyoushi.kotlin")
    id("keiyoushi.lint")
}

android {
    namespace = "eu.kanade.tachiyomi.lib.${project.name}"

    buildFeatures {
        androidResources = false
    }
}

dependencies {
    compileOnly(versionCatalogs.named("libs").findBundle("common").get())
}

tasks.register("printDependentExtensions") {
    doLast {
        project.printDependentExtensions()
    }
}
