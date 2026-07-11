plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Whale Manga"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        name = "WhaleManga"
        lang = "en"
        baseUrl = "https://whalemanga.com"
    }
}
