import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "New Manhwa"
    versionCode = 35
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl {
            mirrors(
                "https://saymanhwa.com",
            )
        }
    }

    deeplink {
        host("saymanhwa.com")
        path("/..*")
    }
}
