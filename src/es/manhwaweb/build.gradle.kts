plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaWeb"
    versionCode = 13
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://manhwaweb.com"
    }
}
