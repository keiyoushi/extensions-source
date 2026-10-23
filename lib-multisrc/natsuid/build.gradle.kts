plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 7
    libVersion = "1.6"

    deeplink {
        path("/manga/..*")
    }
}
