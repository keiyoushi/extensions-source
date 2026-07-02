plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaKatana"
    versionCode = 12
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://mangakatana.com"
    }
}
