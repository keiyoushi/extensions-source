import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ScanR"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://teamscanr.fr"
    }

    deeplink {
        host("teamscanr.fr")
        path("/..*")
    }
}
