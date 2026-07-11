plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Moon Daisy Scans"
    versionCode = 6
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "tr"
        baseUrl = "https://moondaisyscans.pro"
    }
}
