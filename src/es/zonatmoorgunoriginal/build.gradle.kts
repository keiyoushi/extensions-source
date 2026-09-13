import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ZonaTMO.org (unoriginal)"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://zonatmo.org"
    }

    deeplink {
        host("zonatmo.org")
        host("www.zonatmo.org")
        path("/library/..*")
    }
}
