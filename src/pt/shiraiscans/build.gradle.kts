plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Shirai Scans"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://shiraixis.space"
    }
}
