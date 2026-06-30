plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangalek"
    versionCode = 10
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        name = "مانجا ليك"
        lang = "ar"
        baseUrl("https://lekmanga.net") {
            mirrors = listOf(
                "https://lekmanga.online",
                "https://like-manga.net",
                "https://lekmanga.site",
                "https://manga-leko.site",
            )
        }
    }
}
