plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Raven Scans"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://ravenscans.org"
    }
}
