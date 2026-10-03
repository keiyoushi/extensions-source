import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MikoRoku"
    versionCode = 21
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://mikoroku.com"
        versionId = 2
    }

    deeplink {
        host("mikoroku.com")
        host("www.mikoroku.com")
        path("/detail.html")
    }
}
