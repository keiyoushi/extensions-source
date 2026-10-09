import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FSI Comics"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://fsicomics.com"
    }

    source {
        lang = "es"
        baseUrl = "https://es.fsicomics.com"
    }

    source {
        lang = "de"
        baseUrl = "https://de.fsicomics.com"
    }

    source {
        lang = "fr"
        baseUrl = "https://fr.fsicomics.com"
    }

    source {
        lang = "it"
        baseUrl = "https://it.fsicomics.com"
    }

    deeplink {
        path("/..*")
    }
}
