import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "VoraToon"
    pkgName = "id.komikcast"
    versionCode = 86
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    deeplink {
        path("/series/..*")
    }

    source {
        lang = "id"
        baseUrl {
            custom("https://v5.voratoon.com")
        }
        id = 972717448578983812L
    }
}
