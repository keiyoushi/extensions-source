import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ranoa"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://www.ranoa.lat"
    }

    deeplink {
        host("www.ranoa.lat")
        host("ranoa.lat")
        path("/manga")
        path("/manga/..*")
    }
}
