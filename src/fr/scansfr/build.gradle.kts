plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ScansFR"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://scansfr.com"
    }
}
