import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaShot"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://manhwashot.lat"
    }

    deeplink {
        host("manhwashot.lat")
        host("www.manhwashot.lat")
        path("/manga/..*")
        path("/genero/..*")
        path("/explorar/.*")
    }
}
