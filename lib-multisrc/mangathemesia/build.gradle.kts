plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:i18n"))
}

keiyoushi {
    baseVersionCode = 32
    libVersion = "1.4"
}
