plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "4KHD"
    className = "FourKHD"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("zgmz.uuss.uk")
        host("4khd.com")
        path("/content/..*")
    }
}
