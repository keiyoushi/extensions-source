import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

android {
    sourceSets.named("test") {
        kotlin.directories.add("test")
    }
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    testImplementation(libs.bundles.common)
    testImplementation(libs.tachiyomi.lib.v16)
    testImplementation(libs.junit)
}

tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("UnitTestKotlin") }.configureEach {
    enabled = false
}

spotless {
    kotlin {
        target("src/**/*.kt", "test/**/*.kt", "*.kts")
    }
}

keiyoushi {
    name = "Raw FREE"
    versionCode = 3

    contentWarning = ContentWarning.MIXED

    libVersion = "1.6"
    theme = "zingtheme"

    source {
        lang = "ja"
        baseUrl {
            custom("https://rawfree.loan")
        }
    }

    deeplink {
        path("/manga-raw/..*")
    }
}
