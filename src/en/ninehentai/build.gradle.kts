plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NineHentai"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://9hentai.so"
    }

    deeplink {
        host("9hentai.so")
        path("/g/..*")
    }
}
