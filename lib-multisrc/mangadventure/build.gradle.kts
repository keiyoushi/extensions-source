plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 15
    libVersion = "1.4"

    deeplink {
        path("/reader/..*")
    }
}
