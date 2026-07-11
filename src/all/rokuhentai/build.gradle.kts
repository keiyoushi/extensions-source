plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "RokuHentai"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "Roku Hentai"
        lang = "all"
        baseUrl = "https://rokuhentai.com"
    }
}
