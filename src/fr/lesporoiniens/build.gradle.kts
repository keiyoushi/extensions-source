plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Les Poroiniens"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "scanr"

    source {
        lang = "fr"
        baseUrl = "https://lesporoiniens.org"
    }
}
