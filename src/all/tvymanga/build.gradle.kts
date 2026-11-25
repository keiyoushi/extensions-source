import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TvyManga"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    listOf("es", "en").forEach {
        source {
            lang = it
            baseUrl = "https://tvymanga3.com"
        }
    }
}
