plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SayHentai"
    versionCode = 18
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "manhwaz"

    source {
        lang = "vi"
        baseUrl {
            custom("https://sayhentai.cx")
        }
    }
}
