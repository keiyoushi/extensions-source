plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 40
    libVersion = "1.4"

    deeplink {
        path("/..*/vol..*")
    }
}
