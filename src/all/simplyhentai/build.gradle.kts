import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Simply Hentai"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("en", "ja", "zh", "ko", "es", "ru", "fr", "de", "it", "pl").forEach {
        source {
            lang = it
            baseUrl = "https://www.simply-hentai.com"
            versionId = 2
        }
    }
}
