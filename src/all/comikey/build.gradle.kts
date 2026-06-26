plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comikey"
    className = "ComikeyFactory"
    versionCode = 7
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("comikey.com")
        host("br.comikey.com")
        path("/comics/..*/..*/")
    }
}

dependencies {

    implementation(project(":lib:i18n"))
}
