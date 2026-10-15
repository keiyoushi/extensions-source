import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ScanR"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://teamscanr.fr"
    }

    deeplink {
        host("teamscanr.fr")
        path("/..*")
    }
}
