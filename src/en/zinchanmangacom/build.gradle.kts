plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ZinChanManga.com"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://zinchangmanga.net"
    }
}
