plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Xinmeitulu"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://www.xinmeitulu.com"
    }

    deeplink {
        host("xinmeitulu.com")
        host("*.xinmeitulu.com")
        path("/photo/..*")
    }
}
