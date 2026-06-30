plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tr Manga"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "TrManga"
        lang = "tr"
        baseUrl = "https://trmanga.com"
    }
}
