plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SeiManga"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "grouple"

    source {
        baseUrl("https://1.seimanga.me") {
            withCustom = true
        }
        lang = "ru"
    }
}
