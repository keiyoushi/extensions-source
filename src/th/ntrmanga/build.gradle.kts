plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NTR-Manga"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "th"
        baseUrl = "https://www.ntr-manga.net"
    }
}
