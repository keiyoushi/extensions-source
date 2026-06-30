plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kuroi Manga"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://www.kuroimanga.best"
    }
}
