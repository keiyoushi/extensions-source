plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Harem de Kira"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://kiraproject.lat"
        versionId = 2
    }
}
