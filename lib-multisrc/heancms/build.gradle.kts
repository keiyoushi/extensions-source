
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 30

    deeplink {
        path("/.*/..*")
    }
}

dependencies {
    api(project(":lib:i18n"))
}
