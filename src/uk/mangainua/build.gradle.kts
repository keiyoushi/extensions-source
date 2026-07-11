plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaInUa"
    versionCode = 12
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "MANGA/in/UA"
        lang = "uk"
        baseUrl = "https://manga.in.ua"
    }
}
