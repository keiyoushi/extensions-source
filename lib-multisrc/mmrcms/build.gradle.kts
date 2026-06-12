
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 13
}

dependencies {
    api(project(":lib:i18n"))
}
