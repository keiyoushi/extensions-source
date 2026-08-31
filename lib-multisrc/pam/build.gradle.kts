plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:secretstream"))
    implementation(project(":lib:i18n"))
}

keiyoushi {
    baseVersionCode = 3
    libVersion = "1.4"
}
