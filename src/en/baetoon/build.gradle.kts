import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Baetoon"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://baetoon.com"
    }

    deeplink {
        path("/title-detail/..*")
        path("/chapter-detail/..*")
    }
}
