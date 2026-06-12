plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 50

    deeplink {
        path("/.*/..*")
    }
}

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
}
