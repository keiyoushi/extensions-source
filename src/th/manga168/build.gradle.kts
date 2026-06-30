plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga168"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "th"
        baseUrl = "https://manga1688.com"
    }
}
