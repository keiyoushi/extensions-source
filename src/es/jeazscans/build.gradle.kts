import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jeaz Scans"
    versionCode = 68
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://lectorhub.j5z.xyz"
        versionId = 2
    }
}

android {
    sourceSets.getByName("test") {
        java.directories.add("test")
        kotlin.directories.add("test")
    }
}

tasks.matching { it.name == "kspDebugUnitTestKotlin" }.configureEach {
    enabled = false
}

dependencies {
    testImplementation(project(":core"))
    testImplementation(libs.bundles.common)
    testImplementation(libs.kotlin.stdlib)
    testImplementation(libs.tachiyomi.lib.v14)
    testImplementation(libs.junit)
    testImplementation(libs.jsoup)
}
