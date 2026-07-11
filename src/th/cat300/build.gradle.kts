plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Cat300"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "th"
        baseUrl = "https://cat-300.com"
    }
}
