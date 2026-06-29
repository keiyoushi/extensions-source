plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaBuff"
    className = "MangaBuff"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("mangabuff.ru")
        path("/manga/..*")
    }
}
