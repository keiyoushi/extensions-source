plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaGG"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://mangagg.com"
    }
}
