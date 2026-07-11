plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa Scan"
    versionCode = 56
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://manhwascanx.lat"
        id = 7172992930543738693L
    }
}
