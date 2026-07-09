plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "OneManga.info"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangahub"

    source {
        lang = "en"
        baseUrl = "https://onemanga.info"
    }
}
