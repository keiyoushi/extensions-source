plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangadusleri"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "tr"
        baseUrl = "https://mangadusleri.mom"
    }
}
