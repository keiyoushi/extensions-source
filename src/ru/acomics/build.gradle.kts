plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AComics"
    versionCode = 8
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ru"
        baseUrl = "https://acomics.ru"
    }
}
