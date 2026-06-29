plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "24HNovel"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://24hnovel.com"
    }
}
