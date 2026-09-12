plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:secretstream"))
    api(project(":lib:i18n"))
}

keiyoushi {
    baseVersionCode = 4
    libVersion = "1.4"
}
