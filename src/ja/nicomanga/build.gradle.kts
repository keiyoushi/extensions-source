plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nicomanga"
    versionCode = 14
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://nicomanga.com"
    }
}
