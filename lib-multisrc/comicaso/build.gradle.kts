
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 8

    deeplink {
        path("/komik/..*")
    }
}
