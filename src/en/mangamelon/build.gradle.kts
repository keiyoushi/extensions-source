import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaMelon"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://mangamelon.com"
        lang = "en"
    }

    deeplink {
        path("/manga/..*")
        path("/chapter/..*")
    }
}
