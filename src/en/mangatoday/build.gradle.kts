plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaToday"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangahub"

    source {
        lang = "en"
        baseUrl = "https://mangatoday.fun"
    }
}
