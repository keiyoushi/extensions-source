import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Siren Scans"
    versionCode = 22
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "keyoappv2"

    source {
        lang = "en"
        baseUrl = "https://sirenscans.org"
    }
}
