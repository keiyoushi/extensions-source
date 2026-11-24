import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LectorJPG"
    versionCode = 51
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://visorjpg.lat"
        versionId = 3
    }

    deeplink {
        path("/series/..*")
    }
}
