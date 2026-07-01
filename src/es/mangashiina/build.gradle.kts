plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Mukai"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "es"
        baseUrl = "https://mangamukai.com"
        id = 711368877221654433L
    }
}
