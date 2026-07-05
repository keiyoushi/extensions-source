plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VinaHentai"
    versionCode = 11
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://vinahentai.club") {
            withCustom = true
        }
    }
}
