plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 0
    libVersion = "1.6"

    deeplink {
        path("/ru/manga/..*")
    }
}
