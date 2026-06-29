plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ZettaHQ"
    className = "ZettaHQ"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("zettahq.com")
        path("/..*")
    }
}
