plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Chainsaw Man Manga Online"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangacatalog"

    source {
        lang = "en"
        baseUrl = "https://ww5.readchainsawman.com"
    }
}
