plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GlobalComix"
    className = "GlobalComixFactory"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("globalcomix.com")
        path("/c/..*")
    }
}

dependencies {

    implementation(project(":lib:i18n"))
}
