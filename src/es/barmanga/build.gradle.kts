plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BarManga"
    versionCode = 11
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://archiviumbar.com"
    }
}
