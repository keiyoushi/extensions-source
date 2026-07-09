plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ToomTam-Manga"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "th"
        baseUrl = "https://toomtam-manga.com"
    }
}
