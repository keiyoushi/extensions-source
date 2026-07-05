plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangalek"
    versionCode = 11
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        name = "مانجا ليك"
        lang = "ar"
        baseUrl("https://lek-manga.net") {
            mirrors = listOf(
                "https://lekmanga.online",
                "https://like-manga.net",
                "https://lekmanga.site",
                "https://manga-leko.site",
            )
        }
    }
}
