plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WebtoonScan"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://webtoonscan.com"
    }
}
