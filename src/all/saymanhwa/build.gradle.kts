import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SayManhwa"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf(
        "ar", "de", "en", "es", "fil", "fr", "id", "ja", "pt", "th", "vi", "zh-Hans", "zh-Hant",
    ).forEach {
        source {
            lang = it
            baseUrl {
                custom("https://saymanhwa.net")
            }
            if (it == "zh-Hans") {
                id = 6326978194357900073L
            }
        }
        source {
            name = "Say Comics"
            lang = it
            baseUrl = "https://saycomic.com"
        }
    }

    deeplink {
        host("saymanhwa.net")
        host("www.saymanhwa.net")
        host("saycomic.com")
        host("www.saycomic.com")
        path("/series/..*")
        path("/.*/series/..*")
    }
}
