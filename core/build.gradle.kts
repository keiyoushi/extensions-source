plugins {
    id("com.android.library")
    // إضافات أخرى
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)

    alias(kei.plugins.android.base)
    alias(kei.plugins.spotless)
}

android {
    namespace = "keiyoushi.core"

    buildFeatures {
        resValues = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":lib-multisrc:madara"))
    implementation(project(":core"))

    compileOnly(libs.bundles.common)

    testImplementation(libs.bundles.common)
    testImplementation(libs.junit)
}
