import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komiwa"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://www.komiwa.net"
    }

    deeplink {
        host("www.komiwa.net")
        host("komiwa.net")
        path("/manga/..*")
        path("/chapter/..*")
        path("/genero/..*")
        path("/tipo/..*")
        path("/demografia/..*")
    }
}
