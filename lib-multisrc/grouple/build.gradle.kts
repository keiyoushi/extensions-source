plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 46
    libVersion = "1.6"

    deeplink {
        path("/..*")
    }
}
