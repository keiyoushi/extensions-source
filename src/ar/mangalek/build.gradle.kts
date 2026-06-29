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
            mirrors += "https://lekmanga.online"
            mirrors += "https://like-manga.net"
            mirrors += "https://lekmanga.site"
            mirrors += "https://manga-leko.site"
        }
    }
}
