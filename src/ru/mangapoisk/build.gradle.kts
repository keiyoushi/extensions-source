plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaPoisk"
    versionCode = 16
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ru"
        baseUrl = "https://mangapsk.ru"
    }
}
