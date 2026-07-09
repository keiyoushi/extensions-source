plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mundo Manhwa"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://mundomanhwa.com"
    }
}
