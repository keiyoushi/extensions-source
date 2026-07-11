plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa68"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://manhwa68.com"
    }
}
