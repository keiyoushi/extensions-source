import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comick (Unoriginal)"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl {
            mirrors(
                "https://comick.live",
                "https://comick.art",
            )
        }
    }
    deeplink {
        path("/comic/..*")
    }
}
