plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Scan-Manga"
    versionCode = 23
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        baseUrl = "https://m.scan-manga.com"
        lang = "fr"
    }
}
