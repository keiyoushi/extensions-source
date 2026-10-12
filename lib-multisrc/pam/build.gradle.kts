plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:secretstream"))
    api(project(":lib:i18n"))
    implementation(project(":lib:ece"))
}

keiyoushi {
    baseVersionCode = 3
    libVersion = "1.6"
}
