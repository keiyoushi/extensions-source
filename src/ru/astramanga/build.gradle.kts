import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AstraManga"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ru"
        baseUrl = "https://astramanga.org"
    }

    deeplink {
        path("/manga/..*")
    }
}
