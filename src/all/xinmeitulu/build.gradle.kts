plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Xinmeitulu"
    className = "Xinmeitulu"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("xinmeitulu.com")
        host("*.xinmeitulu.com")
        path("/photo/..*")
    }
}
