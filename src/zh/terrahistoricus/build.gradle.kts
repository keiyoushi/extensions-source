import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Terra Historicus"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "泰拉记事社"
        lang = "zh"
        baseUrl = "https://comic.hypergryph.com"
    }
}

android {
    sourceSets.named("test") {
        java.directories.clear()
        java.directories.add("test")
        kotlin.directories.clear()
        kotlin.directories.add("test")
        resources.directories.clear()
        resources.directories.add("test-resources")
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.json)
    testImplementation(libs.tachiyomi.lib.v16)
}
