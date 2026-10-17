plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 43
    libVersion = "1.6"

    deeplink {
        path("/..*")
    }
}
