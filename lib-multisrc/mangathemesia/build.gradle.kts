
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 32

    deeplink {
        path("/.*/..*")
        path("/..*")
    }
}

dependencies {
    api(project(":lib:i18n"))
}
