plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:i18n"))
}

keiyoushi {
    baseVersionCode = 23
    libVersion = "1.4"

    deeplink {
        path("/.*/..*")
    }
}
