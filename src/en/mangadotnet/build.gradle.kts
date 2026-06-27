plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangadotnet"
    className = "Mangadotnet"
    versionCode = 12
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("mangadot.net")
        path("/manga/..*")
        path("/chapter/..*")
    }
}
