plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhatic"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "ar"
        baseUrl = "https://manhatic.com"
    }
}
