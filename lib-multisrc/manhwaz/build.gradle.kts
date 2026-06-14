
plugins {
    alias(kei.plugins.multisrc)
}

multisrc {
    baseVersionCode = 5
}

dependencies {
    api(project(":lib:i18n"))
}
