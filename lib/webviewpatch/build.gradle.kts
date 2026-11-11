plugins {
    alias(kei.plugins.library)
}

android {
    // Tests live outside `src` (the lib's single main source dir, set by the library convention plugin),
    // so the main compilation doesn't pick them up. Oracle fixture on the test resources classpath.
    sourceSets {
        named("test") {
            kotlin.directories.clear()
            kotlin.directories.add("test")
            resources.directories.clear()
            resources.directories.add("test-resources")
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
