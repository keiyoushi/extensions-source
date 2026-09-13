import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SayManhwa"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf(
        "ar", "de", "en", "es", "fil", "fr", "id", "ja", "pt", "th", "vi", "zh",
    ).forEach {
        source {
            lang = it
            baseUrl {
                custom("https://saymanhwa.com")
            }
        }
    }

    deeplink {
        path("/.*/series/..*")
    }
}
