plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Romance"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://mangaromance19.com"
    }
}
