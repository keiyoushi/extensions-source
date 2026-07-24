plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:i18n"))
}

keiyoushi {
    baseVersionCode = 24
    libVersion = "1.6"

    deeplink {
        path("/.*/..*")
    }
}
