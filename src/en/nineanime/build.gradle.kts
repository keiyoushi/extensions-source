plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NineAnime"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.nineanime.com"
    }
}
