plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komikindo"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://komikindo.bid"
    }
}
