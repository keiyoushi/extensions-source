plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ZinChanManga"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://zinchangmanga.net"
        versionId = 2
    }
}
