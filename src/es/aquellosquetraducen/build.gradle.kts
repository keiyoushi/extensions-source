import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Aquellos Que Traducen Scan"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://aquellosquetraducen.com"
    }

    deeplink {
        path("/obra.html")
        path("/lector.html")
    }
}
