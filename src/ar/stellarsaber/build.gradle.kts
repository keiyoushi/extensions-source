import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "StellarSaber"
    versionCode = 33
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ar"
        baseUrl = "https://stellarsaber.pro"
    }

    deeplink {
        path("/.*/..*")
    }
}
