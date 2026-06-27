plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LikeManga"
    className = "LikeManga"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("likemanga.io")
        path("/..*")
    }
}
