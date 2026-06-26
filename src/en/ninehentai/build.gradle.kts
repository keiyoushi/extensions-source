plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NineHentai"
    className = "NineHentai"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("9hentai.so")
        path("/g/..*")
    }
}
