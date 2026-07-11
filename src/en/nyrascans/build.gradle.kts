plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nyra Scans"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "keyoapp"

    source {
        lang = "en"
        baseUrl = "https://nyrascans.com"
    }
}
