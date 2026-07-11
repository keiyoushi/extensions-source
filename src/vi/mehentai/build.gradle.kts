plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MeHentai"
    versionCode = 6
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "manhwaz"

    source {
        lang = "vi"
        baseUrl = "https://mehentai.blog"
    }
}
