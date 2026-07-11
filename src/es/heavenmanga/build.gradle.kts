plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HeavenManga"
    versionCode = 9
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://heavenmanga.com"
    }
}
