plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MintManga"
    versionCode = 47
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "grouple"

    source {
        baseUrl("https://2.mintmanga.one") {
            withCustom = true
        }
        lang = "ru"
        id = 6L
    }
}
