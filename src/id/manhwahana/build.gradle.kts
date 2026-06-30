plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwahana"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "id"
        baseUrl = "https://manhwahana.com"
    }
}
