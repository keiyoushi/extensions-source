import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GoComics"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://www.gocomics.com"
    }

    source {
        lang = "es"
        baseUrl = "https://www.gocomics.com"
    }

    deeplink {
        path("/..*")
    }
}
