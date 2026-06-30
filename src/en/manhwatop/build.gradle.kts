plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwatop"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://manhwatop.com"
    }
}
