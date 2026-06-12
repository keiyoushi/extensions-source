
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 1
}

dependencies {
    api(project(":lib:i18n"))
}
