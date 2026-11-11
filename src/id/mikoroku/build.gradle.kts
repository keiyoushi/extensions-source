import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MikoRoku"
    versionCode = 24
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://mikoroku.top"
        versionId = 2
    }

    deeplink {
        host("mikoroku.top")
        host("www.mikoroku.top")
        path("/series/..*")
    }
}
