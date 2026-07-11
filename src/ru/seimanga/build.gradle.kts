plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SeiManga"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "grouple"

    source {
        baseUrl {
            custom("https://1.seimanga.me")
        }
        lang = "ru"
    }
}
