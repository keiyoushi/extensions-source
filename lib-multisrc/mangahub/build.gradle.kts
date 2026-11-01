plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 40
    libVersion = "1.6"

    deeplink {
        path("/manga/..*")
        path("/chapter/..*")
    }
}
