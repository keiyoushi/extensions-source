plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaLionz"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "ar"
        baseUrl = "https://manga-lionz.org"
    }
}
