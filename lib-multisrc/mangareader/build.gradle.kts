plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 4
    libVersion = "1.6"

    deeplink {
        path("/read/..*")
        path("/manga/..*")
        path("/raw-manga/..*")
    }
}
