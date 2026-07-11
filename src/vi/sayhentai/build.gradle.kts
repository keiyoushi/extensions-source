plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SayHentai"
    versionCode = 18
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "manhwaz"

    source {
        lang = "vi"
        baseUrl {
            custom("https://sayhentai.cx")
        }
    }
}
