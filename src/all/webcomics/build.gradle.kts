import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Webcomics"
    versionCode = 11
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    listOf("en", "fr", "pt", "es", "id").forEach {
        source {
            lang = it
            baseUrl = "https://webcomicsapp.com"
            versionId = 2
        }

        deeplink {
            path("/$it/..*/..*/..*")
        }
    }
}
