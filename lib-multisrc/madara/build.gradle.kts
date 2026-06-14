plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 51

    deeplink {
        path("/.*/..*")
    }
}

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
}
