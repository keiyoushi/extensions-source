
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 42

    deeplink {
        path("/ru/manga/..*")
    }
}
