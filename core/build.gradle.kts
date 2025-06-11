plugins {
    id("keiyoushi.android.library")
    id("keiyoushi.kotlin")
    id("keiyoushi.lint")
}

android {
    namespace = "keiyoushi.core"

    buildFeatures {
        resValues = false
    }
}

dependencies {
    compileOnly(versionCatalogs.named("libs").findBundle("common").get())
}
