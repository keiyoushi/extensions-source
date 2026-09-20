plugins {
    alias(kei.plugins.library)
}

android {
    sourceSets {
        named("test") {
            java.directories.clear()
            java.directories.add("test")
            kotlin.directories.clear()
            kotlin.directories.add("test")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation(libs.bundles.common)
    testImplementation(libs.tachiyomi.lib.v16)
    testImplementation(libs.junit)
}
