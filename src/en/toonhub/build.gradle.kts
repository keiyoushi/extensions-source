import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toon Hub"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://toon-hub.com"
    }

    deeplink {
        path("/comics/..*")
        path("/en/comics/..*")
    }
}
