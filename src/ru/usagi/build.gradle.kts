plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Usagi"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "grouple"

    source {
        baseUrl("https://web.usagi.one") {
            withCustom = true
        }
        lang = "ru"
    }
}
