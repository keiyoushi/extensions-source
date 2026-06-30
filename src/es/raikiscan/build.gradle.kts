plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Raiki Scan"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "es"
        baseUrl = "https://raikiscan.com"
    }
}
