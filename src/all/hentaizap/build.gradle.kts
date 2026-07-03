plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiZap"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "galleryadults"

    listOf("en", "ja", "es", "fr", "ko", "de", "ru", "all").forEach { language ->
        source {
            lang = language
            baseUrl = "https://hentaizap.com"
        }
    }
}
