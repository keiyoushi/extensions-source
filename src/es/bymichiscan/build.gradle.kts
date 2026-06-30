plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Bymichi Scan"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "es"
        baseUrl = "https://bymichiby.com"
    }
}
