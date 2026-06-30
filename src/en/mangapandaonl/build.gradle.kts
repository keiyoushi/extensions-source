plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaPanda.onl"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangahub"

    source {
        lang = "en"
        baseUrl = "https://mangapanda.onl"
    }
}
