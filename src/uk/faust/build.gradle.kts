import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Faust"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "uk"
        baseUrl = "https://faust-web.com"
    }

    deeplink {
        path("/manga/..*")
    }
}
