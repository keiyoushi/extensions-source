plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa-raw"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "all"
        baseUrl = "https://manhwa-raw.com"
    }
}
