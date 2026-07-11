plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yupmanga"
    versionCode = 16
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://www.yupmanga.com"
    }
}
