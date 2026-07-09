plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KumaPoi"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://kumapoi.info"
    }
}
