plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AllManga"
    className = "AllManga"
    versionCode = 19
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("allmanga.to")
        path("/manga/..*")
        path("/read/..*")
    }
}
