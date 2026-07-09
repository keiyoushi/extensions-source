plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Goon Scans"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "ar"
        baseUrl = "https://goonscans.org"
    }
}
