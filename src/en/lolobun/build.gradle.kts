import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LoLoBun"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://www.lolobun.com"
        lang = "en"
    }

    deeplink {
        host("www.lolobun.com")
        host("lolobun.com")
        path("/c/..*")
    }
}
