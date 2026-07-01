plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MeHentai"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "manhwaz"

    source {
        lang = "vi"
        baseUrl = "https://mehentai.blog"
    }
}
