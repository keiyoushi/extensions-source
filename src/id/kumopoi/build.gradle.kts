plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KumoPoi"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://kumopoi.org"
    }
}
