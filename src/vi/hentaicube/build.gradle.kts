plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CBHentai"
    className = "HentaiCB"
    versionCode = 31
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"
    baseUrl = "https://2tencb.pro"

    deeplink {
        path("/read/..*")
    }
}
