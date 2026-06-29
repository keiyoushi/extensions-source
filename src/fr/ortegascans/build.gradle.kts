plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ortega Scans"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://ortegascans.fr"
    }
}
