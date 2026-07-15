plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)

    alias(kei.plugins.android.base)
    alias(kei.plugins.spotless)
}

android {
    namespace = "keiyoushi.core"

    buildFeatures {
        resValues = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    compileOnly(libs.bundles.common)
    compileOnly(libs.tachiyomi.lib.v16)

    testImplementation(libs.bundles.common)
    testImplementation(libs.tachiyomi.lib.v16)
    testImplementation(libs.junit)
}
