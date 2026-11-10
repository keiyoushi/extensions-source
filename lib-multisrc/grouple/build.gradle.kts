plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 45
    libVersion = "1.6"

    deeplink {
        path("/..*")
    }
}
