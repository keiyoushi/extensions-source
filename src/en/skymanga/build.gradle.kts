plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sky Manga"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://skymanga.work"
    }
}
