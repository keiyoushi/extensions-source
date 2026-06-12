
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 20
}

dependencies {
    api(project(":lib:i18n"))
}
