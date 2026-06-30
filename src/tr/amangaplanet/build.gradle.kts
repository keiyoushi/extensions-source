plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Amanga Planet"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "tr"
        baseUrl = "https://www.amangaplanet.com.tr"
    }
}
