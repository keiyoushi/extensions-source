plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "XXX Yaoi"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://3xyaoi.com"
    }
}
