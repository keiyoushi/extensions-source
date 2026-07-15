import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MayoTune"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("en", "ja").forEach {
        source {
            lang = it
            baseUrl = "https://mayochuu.xyz"
        }
    }
}
