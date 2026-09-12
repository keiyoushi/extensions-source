import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LectorTMO"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        baseUrl = "https://lectortmo.vip"
        lang = "es"
    }

    deeplink {
        path("/..*")
    }
}
