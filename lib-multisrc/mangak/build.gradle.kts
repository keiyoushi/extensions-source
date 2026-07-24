plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 1
    libVersion = "1.6"

    deeplink {
        path("/.*/..*")
    }
}
