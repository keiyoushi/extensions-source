plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaHub"
    className = "MangaHubIo"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangahub"
    baseUrl = "https://mangahub.io"
}
