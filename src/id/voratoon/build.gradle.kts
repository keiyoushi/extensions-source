import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VoraToon"
    pkgName = "id.komikcast"
    versionCode = 83
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://v1.voratoon.com"
        id = 972717448578983812L
    }
}
