plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 2
    libVersion = "1.4"

    deeplink {
        path("/manga/..*")
        path("/read/..*")
    }
}
