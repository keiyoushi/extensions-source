plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 39
    libVersion = "1.6"

    deeplink {
        path("/manga/..*")
        path("/chapter/..*")
    }
}
