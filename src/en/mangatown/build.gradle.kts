plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangatown"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.mangatown.com"
    }
}
