plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:i18n"))
    implementation(project(":lib:webview"))
}

keiyoushi {
    baseVersionCode = 10
    libVersion = "1.6"
}
