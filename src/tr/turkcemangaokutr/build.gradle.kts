plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Turkce Manga Oku TR"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        name = "Türkçe Manga Oku TR"
        lang = "tr"
        baseUrl = "https://turkcemangaoku.com.tr"
    }
}
