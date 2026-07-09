plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Şehri"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://manga-sehri.com"
    }
}
