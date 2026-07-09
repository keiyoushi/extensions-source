plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KuraManga"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://kuramanga.com"
    }
}
