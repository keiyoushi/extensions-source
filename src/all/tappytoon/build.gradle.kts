import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tappytoon"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf("en", "fr", "de").forEach {
        source {
            lang = it
            baseUrl = "https://www.tappytoon.com/$it"
        }
    }
}
