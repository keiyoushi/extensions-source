plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kun Manga Online"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://www.kunmanga.online"
    }
}
