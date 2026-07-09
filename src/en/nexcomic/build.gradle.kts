plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NexComic"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://nexcomic.com"
    }
}
