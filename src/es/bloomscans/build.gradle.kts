plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Bloom Scans"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "es"
        baseUrl = "https://bloomscans.com"
    }
}
