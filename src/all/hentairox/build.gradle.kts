plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiRox"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "galleryadults"

    listOf("en", "ja", "zh", "all").forEach { language ->
        source {
            lang = language
            baseUrl = "https://hentairox.com"
        }
    }
}
