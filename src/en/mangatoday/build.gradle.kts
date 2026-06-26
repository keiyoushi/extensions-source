plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaToday"
    className = "MangaToday"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangahub"
    baseUrl = "https://mangatoday.fun"
}
