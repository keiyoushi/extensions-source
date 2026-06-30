plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Uncensored Manhwa"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    listOf("en", "all").forEach {
        source {
            lang = it
            baseUrl = "https://uncensoredmanhwa.us"
        }
    }
}
