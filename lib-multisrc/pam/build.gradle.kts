plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:secretstream"))
    api(project(":lib:i18n"))
    implementation(project(":lib:ece"))
    implementation(project(":lib:webview"))
}

keiyoushi {
    baseVersionCode = 5
    libVersion = "1.6"
}
