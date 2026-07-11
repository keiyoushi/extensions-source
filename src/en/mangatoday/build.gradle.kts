plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaToday"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangahub"

    source {
        lang = "en"
        baseUrl = "https://mangatoday.fun"
    }
}
