import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "InsanosScan"
    versionCode = 31
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://insanoslibrary.com"
        versionId = 2
    }

    deeplink {
        host("insanoslibrary.com")
        path("/manga/..*")
    }
}
