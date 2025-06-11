plugins {
    id("keiyoushi.android.library")
    id("keiyoushi.kotlin")
    id("keiyoushi.lint")
}

android {
    namespace = "eu.kanade.tachiyomi.multisrc.${project.name}"

    sourceSets {
        named("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.setSrcDirs(listOf("src"))
            res.setSrcDirs(listOf("res"))
            assets.setSrcDirs(listOf("assets"))
        }
    }
}

dependencies {
    compileOnly(versionCatalogs.named("libs").findBundle("common").get())
    implementation(project(":core"))
}

tasks.register("printDependentExtensions") {
    doLast {
        project.printDependentExtensions()
    }
}
