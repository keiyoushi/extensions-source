
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 9

    deeplink {
        path("/g.*/..*/")
    }
}
