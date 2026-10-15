import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Zonatmo.to (unoriginal)"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://zonatmo.to"
    }

    deeplink {
        host("zonatmo.to")
        host("www.zonatmo.to")
        path("/manga/..*")
    }
}
