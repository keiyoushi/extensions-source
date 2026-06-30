plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa 18 Uncensored"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    listOf("en", "all").forEach {
        source {
            lang = it
            baseUrl = "https://manhwa18uncensored.com"
        }
    }
}
