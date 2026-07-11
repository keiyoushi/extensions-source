plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HoneyManga"
    versionCode = 8
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "uk"
        baseUrl = "https://honey-manga.com.ua"
    }
}
