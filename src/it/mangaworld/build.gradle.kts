import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangaworld"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangaworld"

    source {
        lang = "it"
        baseUrl = "https://www.mangaworld.me"
    }
}

android.sourceSets.named("test") { kotlin.directories.add("test") }

// The source generator runs on extension classes, not test fixtures.
tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("UnitTestKotlin") }.configureEach { enabled = false }

dependencies {
    testImplementation(libs.kotlin.stdlib)
    testImplementation(libs.jsoup)
    testImplementation(libs.junit)
}
