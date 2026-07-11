plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ota Scans"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "id"
        baseUrl = "https://yurilabs.my.id"
    }
}
