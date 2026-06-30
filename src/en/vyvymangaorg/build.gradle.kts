plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VyvyManga.org"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://vyvymanga.org"
    }
}
