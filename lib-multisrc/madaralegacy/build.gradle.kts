plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
}

keiyoushi {
    baseVersionCode = 52
    libVersion = "1.4"

    deeplink {
        path("/.*/..*")
    }
}
