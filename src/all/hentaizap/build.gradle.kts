import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiZap"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "galleryadults"

    listOf("en", "ja", "es", "fr", "ko", "de", "ru", "all").forEach { language ->
        source {
            lang = language
            baseUrl = "https://hentaizap.com"
        }
    }
}
