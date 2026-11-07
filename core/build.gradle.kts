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
    // Newest stable androidx.webkit, to check the WebView glue against
    testImplementation("androidx.webkit:webkit:[1.18.0,)")
    testImplementation(libs.junit)
}

configurations.configureEach {
    resolutionStrategy.componentSelection.all {
        if (candidate.group == "androidx.webkit" && Regex("-(alpha|beta|rc)").containsMatchIn(candidate.version)) {
            reject("pre-release")
        }
    }
}
