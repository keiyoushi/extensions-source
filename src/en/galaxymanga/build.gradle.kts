plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Galaxy Manga"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://galaxymanga.io"
    }
}
