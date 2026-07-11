plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Cerise Scan"
    versionCode = 11
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://loverstoon.com"
        versionId = 3
    }
}
