plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 7
    libVersion = "1.4"

    deeplink {
        path("/.*/.*/..*")
    }
}
