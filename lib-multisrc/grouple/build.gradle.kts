plugins {
    alias(kei.plugins.multisrc)
}

keiyoushi {
    baseVersionCode = 41
    libVersion = "1.4"

    deeplink {
        path("/..*/vol..*")
    }
}
