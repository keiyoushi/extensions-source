import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "One Piece Fans"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("es", "en").forEach {
        source {
            lang = it
            baseUrl = "https://one-piece-fans2.com"
        }
    }
}
