plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KuraManga"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://kuramanga.com"
    }
}
