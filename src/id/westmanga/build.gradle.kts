import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "West Manga"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl {
            mirrors(
                "https://v1.westmanga.my",
                "https://v1.westmanga.top",
            )
        }
        id = 8883916630998758688L
    }

    deeplink {
        path("/comic/..*")
        path("/manga/..*")
    }
}
