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
    compileOnly(libs.okhttp.brotli)
    compileOnly(libs.okhttp.logging)
    compileOnly(libs.okhttp.dnsOverHttps)

    testImplementation(libs.bundles.common)
    testImplementation(libs.junit)
}
