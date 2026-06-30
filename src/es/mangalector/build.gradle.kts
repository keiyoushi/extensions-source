plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaLector"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://mangalector.com"
    }
}
