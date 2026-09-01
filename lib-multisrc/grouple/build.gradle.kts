plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 42
    libVersion = "1.6"

    deeplink {
        path("/..*")
    }
}
