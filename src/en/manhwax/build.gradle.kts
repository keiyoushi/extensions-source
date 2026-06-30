plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwax"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://manhwax.top"
    }
}
