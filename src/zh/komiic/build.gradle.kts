plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komiic"
    className = "Komiic"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("komiic.com")
        path("/comic/..*")
    }
}
