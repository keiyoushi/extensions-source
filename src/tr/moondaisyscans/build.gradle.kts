plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Moon Daisy Scans"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "tr"
        baseUrl = "https://moondaisyscans.pro"
    }
}
