plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 41
    libVersion = "1.6"

    deeplink {
        path("/..*")
    }
}
