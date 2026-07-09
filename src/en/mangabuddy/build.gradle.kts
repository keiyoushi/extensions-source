plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaK"
    versionCode = 30
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://mangak.io"
        id = 5020395055978987501L
    }
}
