import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TuMangaOnline"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        baseUrl = "https://zonatmo.org"
        lang = "es"
    }

    deeplink {
        path("/..*")
    }
}
