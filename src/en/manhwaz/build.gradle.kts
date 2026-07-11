plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaZ"
    versionCode = 37
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "manhwaz"

    source {
        lang = "en"
        baseUrl = "https://manhwaz.com"
    }
}
