
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 23

    deeplink {
        path("/.*/..*")
    }
}

dependencies {
    api(project(":lib:i18n"))
}
