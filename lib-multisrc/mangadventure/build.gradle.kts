
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 15

    deeplink {
        path("/reader/..*")
    }
}
