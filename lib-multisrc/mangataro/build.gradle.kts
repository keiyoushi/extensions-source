
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 1

    deeplink {
        path("/manga/..*")
        path("/read/..*")
    }
}
