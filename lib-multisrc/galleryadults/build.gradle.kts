plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 9
    libVersion = "1.4"

    deeplink {
        path("/g.*/..*/")
    }
}
