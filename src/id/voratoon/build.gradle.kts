import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VoraToon"
    pkgName = "id.komikcast"
    versionCode = 84
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    deeplink {
        path("/series/..*")
    }

    source {
        lang = "id"
        baseUrl = "https://v2.voratoon.com"
        id = 972717448578983812L
    }
}
