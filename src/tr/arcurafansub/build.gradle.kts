plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Arcura Fansub"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "tr"
        baseUrl = "https://arcurafansub.com"
    }
}
