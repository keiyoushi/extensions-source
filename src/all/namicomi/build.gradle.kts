plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NamiComi"
    className = "NamiComiFactory"
    versionCode = 6
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("namicomi.com")
        path("/.*/title/..*")
    }
}

dependencies {

    implementation(project(":lib:i18n"))
}
