plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BiliManga"
    className = "BiliManga"
    versionCode = 11
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("www.bilimanga.net")
        path("/detail/.*\\.html")
    }
}
