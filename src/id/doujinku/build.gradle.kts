plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Doujinku"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl {
            custom("https://doujinku.org")
        }
    }
}
