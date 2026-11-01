import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MHScans"
    versionCode = 71
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://mhscans.com"
        versionId = 2
    }

    deeplink {
        path("/comics/..*")
    }
}
