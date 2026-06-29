plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaInUa"
    versionCode = 12
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "MANGA/in/UA"
        lang = "uk"
        baseUrl = "https://manga.in.ua"
    }
}
