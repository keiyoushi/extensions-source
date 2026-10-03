plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:secretstream"))
    api(project(":lib:i18n"))
    api("com.dylibso.chicory:runtime:1.4.0")
}

keiyoushi {
    baseVersionCode = 1
    libVersion = "1.6"
}
