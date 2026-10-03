plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:secretstream"))
    api(project(":lib:i18n"))
    implementation("com.dylibso.chicory:runtime:1.7.5")
}

keiyoushi {
    baseVersionCode = 0
    libVersion = "1.6"
}
