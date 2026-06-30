plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tarot Scans"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "tr"
        baseUrl = "https://www.tarotscans.com"
    }
}
