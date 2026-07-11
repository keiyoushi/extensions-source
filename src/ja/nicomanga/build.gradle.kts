plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nicomanga"
    versionCode = 14
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://nicomanga.com"
    }
}
