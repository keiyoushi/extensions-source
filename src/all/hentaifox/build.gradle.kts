plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiFox"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "galleryadults"

    listOf("en", "ja", "zh", "ko", "all").forEach { language ->
        source {
            lang = language
            baseUrl = "https://hentaifox.com"
        }
    }
}
