plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 34
    libVersion = "1.6"

    deeplink {
        path("/.*/..*")
    }
}
