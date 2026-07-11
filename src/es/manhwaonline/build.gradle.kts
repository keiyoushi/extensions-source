plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaOnline"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://manhwa-online.com"
    }
}
