plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 32
    libVersion = "1.6"

    deeplink {
        path("/.*/..*")
    }
}
