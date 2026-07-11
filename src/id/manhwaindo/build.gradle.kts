plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa Indo"
    versionCode = 11
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://www.manhwaindo.my"
    }
}
