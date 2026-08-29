plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 10
    libVersion = "1.6"

    deeplink {
        path("/g.*/..*/")
    }
}
