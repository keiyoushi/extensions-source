plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sunset Manga"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://sunsetmanga.com"
    }
}
