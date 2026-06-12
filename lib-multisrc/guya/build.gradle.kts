
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 7

    deeplink {
        path("/.*/.*/..*")
    }
}
