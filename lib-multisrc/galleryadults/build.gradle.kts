plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 9
    libVersion = "1.6"

    deeplink {
        path("/g.*/..*/")
    }
}
