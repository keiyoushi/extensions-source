
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 8
}

dependencies {
    api(project(":lib:i18n"))
}
