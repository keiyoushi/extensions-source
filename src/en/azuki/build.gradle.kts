plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Omoi"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.omoi.com"
        versionId = 2
    }
}
