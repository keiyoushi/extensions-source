plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "EpicManga"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://epicmanga.co"
    }
}
