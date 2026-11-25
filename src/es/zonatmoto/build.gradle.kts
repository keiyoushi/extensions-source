import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Zonatmo.to (unoriginal)"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl {
            mirrors(
                "https://zonatmo.to",
                "https://otakuteca.net",
            )
        }
    }

    deeplink {
        host("zonatmo.to")
        host("www.zonatmo.to")
        host("otakuteca.net")
        path("/manga/..*")
    }
}
