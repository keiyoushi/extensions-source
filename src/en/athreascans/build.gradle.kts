plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Athrea Scans"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://athreascans.com"
    }
}
