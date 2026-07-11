plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VinaHentai"
    versionCode = 11
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl {
            custom("https://vinahentai.club")
        }
    }
}
