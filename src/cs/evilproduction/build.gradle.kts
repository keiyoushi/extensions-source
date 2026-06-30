plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Evil production"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "cs"
        baseUrl = "https://evil-manga.eu"
    }
}
