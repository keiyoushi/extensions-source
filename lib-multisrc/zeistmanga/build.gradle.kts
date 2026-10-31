plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:i18n"))
}

keiyoushi {
    baseVersionCode = 15
    libVersion = "1.6"

    deeplink {
        path("/..*/..*/..*.html")
    }
}
