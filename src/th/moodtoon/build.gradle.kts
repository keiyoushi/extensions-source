plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Moodtoon"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "th"
        baseUrl = "https://moon-toon.com"
    }
}
