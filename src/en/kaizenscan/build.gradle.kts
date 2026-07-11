plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kaizen Scan"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "keyoapp"

    source {
        lang = "en"
        baseUrl = "https://kaizenscan.com"
    }
}
