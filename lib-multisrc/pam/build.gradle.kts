plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:secretstream"))
    api(project(":lib:i18n"))
    implementation(project(":lib:ece"))
    implementation("com.dylibso.chicory:runtime:1.7.5")
}

keiyoushi {
    baseVersionCode = 1
    libVersion = "1.6"
}
