plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Coffee Manga"
    versionCode = 5
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://coffeemanga.ink"
    }
}
