plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Türkçe Manga Oku"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://trmangaoku.com"
    }
}
