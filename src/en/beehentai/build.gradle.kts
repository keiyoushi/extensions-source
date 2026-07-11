plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BeeHentai"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madtheme"

    source {
        lang = "en"
        baseUrl = "https://beehentai.com"
    }
}
