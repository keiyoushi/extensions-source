plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Simply Hentai"
    versionCode = 8
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    listOf("en", "ja", "zh", "ko", "es", "ru", "fr", "de", "it", "pl").forEach {
        source {
            lang = it
            baseUrl = "https://www.simply-hentai.com"
            versionId = 2
        }
    }
}
