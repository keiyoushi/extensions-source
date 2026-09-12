import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LanorTrad"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://lanortrad.com"
    }

    deeplink {
        path("/manga/..*")
        path("/manga.html")
        path("/reader.html")
    }
}
