plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:i18n"))
}

keiyoushi {
    baseVersionCode = 18
    libVersion = "1.6"

    deeplink {
        path("/..*/..*/..*.html")
    }
}
