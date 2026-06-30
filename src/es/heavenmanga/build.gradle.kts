plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HeavenManga"
    versionCode = 9
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://heavenmanga.com"
    }
}
