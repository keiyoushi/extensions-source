plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Bymichi Scan"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "es"
        baseUrl = "https://bymichiby.com"
    }
}
