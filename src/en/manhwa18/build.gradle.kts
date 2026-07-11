plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa18"
    versionCode = 13
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://manhwa18.com"
    }
}
