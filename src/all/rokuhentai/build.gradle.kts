plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "RokuHentai"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "Roku Hentai"
        lang = "all"
        baseUrl = "https://rokuhentai.com"
    }
}
