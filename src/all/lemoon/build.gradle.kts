import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lemoon"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://lemoon.io"
    }

    deeplink {
        host("lemoon.io")
        host("www.lemoon.io")
        path("/series/..*")
        path("/..*/series/..*")
        path("/@..*")
        path("/..*/@..*")
    }
}
