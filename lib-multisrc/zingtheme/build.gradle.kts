plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 3
    libVersion = "1.6"
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

spotless {
    kotlin {
        target("src/**/*.kt", "test/**/*.kt", "*.kts")
    }
}
