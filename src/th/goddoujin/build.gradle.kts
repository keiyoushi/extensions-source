plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "God-Doujin"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "th"
        baseUrl = "https://god-doujin.com"
    }
}
