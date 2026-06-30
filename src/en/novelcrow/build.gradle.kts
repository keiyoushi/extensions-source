plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NovelCrow"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://novelcrow.com"
    }
}
