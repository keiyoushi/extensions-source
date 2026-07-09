plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Raw Otaku"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangareader"

    source {
        lang = "ja"
        baseUrl = "https://rawotaku.com"
    }
}
