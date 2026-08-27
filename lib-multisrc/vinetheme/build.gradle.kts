plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 2
    libVersion = "1.6"

    deeplink {
        path("/series/comic/..*")
    }
}
