plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaKatana"
    versionCode = 12
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://mangakatana.com"
    }
}
