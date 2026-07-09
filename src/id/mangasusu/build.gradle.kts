plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangasusu"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://mangasusuku.com"
    }
}
