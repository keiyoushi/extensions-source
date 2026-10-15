import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Scantrad Union"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://scantrad-union.com"
    }

    deeplink {
        host("scantrad-union.com")
        path("/manga/..*")
        path("/projets/..*")
        path("/read/..*")
    }
}
