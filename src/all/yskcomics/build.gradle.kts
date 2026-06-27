plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YSK Comics"
    className = "YSKComicsFactory"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("www.ysk-comics.com")
        path("/ar/comic/..*")
        path("/en/comic/..*")
    }
}
