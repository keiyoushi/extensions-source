plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga.uno"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://manga.uno"
    }
}
