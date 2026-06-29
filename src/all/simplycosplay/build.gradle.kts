plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Simply Cosplay"
    className = "SimplyCosplay"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("www.simply-cosplay.com")
        host("simply-cosplay.com")
        path("/gallery/..*")
        path("/image/..*")
    }
}
