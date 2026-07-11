plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ortega Scans"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://ortegascans.fr"
    }
}
