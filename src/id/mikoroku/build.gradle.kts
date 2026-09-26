import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MikoRoku"
    versionCode = 22
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://mikoroku.com"
        id = 8593493873810750465L
        versionId = 2
    }

    deeplink {
        path("/detail")
        path("/detail.html")
    }
}
