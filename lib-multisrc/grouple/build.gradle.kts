
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 40

    deeplink {
        path("/..*/vol..*")
    }
}
