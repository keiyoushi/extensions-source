import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nora no Fansub"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "foolslide"

    source {
        lang = "es"
        baseUrl = "https://www.noranofansub.com"
    }

    deeplink {
        host("www.noranofansub.com")
        host("noranofansub.com")
        path("/lector/series/..*")
        path("/lector/read/..*")
    }
}
