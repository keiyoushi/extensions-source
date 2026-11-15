import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Alkhariqun"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://skrolls.alkhariqun.com"
        lang = "ar"
    }

    deeplink {
        path("/comics/..*")
        path("/share/..*")
    }
}
