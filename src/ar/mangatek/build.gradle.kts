import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

android {
    sourceSets.named("test") {
        java.directories.add("test")
        kotlin.directories.add("test")
        resources.directories.add("test-resources")
    }
}

// Source codegen only runs on the extension; unit tests consume its generated classes.
tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("UnitTestKotlin") }.configureEach {
    enabled = false
}

spotless {
    kotlin {
        target("src/**/*.kt", "test/**/*.kt", "*.kts")
    }
}

dependencies {
    testImplementation(libs.bundles.common)
    testImplementation(libs.tachiyomi.lib.v16)
    testImplementation(libs.junit)
}

keiyoushi {
    name = "MangaTek"
    versionCode = 11
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ar"
        baseUrl = "https://mangatek.com"
    }

    deeplink {
        path("/manga/..*")
        path("/reader/..*")
    }
}
