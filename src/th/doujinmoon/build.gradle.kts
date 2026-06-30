plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Doujin Moon"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "th"
        baseUrl = "https://doujinmoon.com"
    }
}
