import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BDplus"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://bdplus.cc"
    }

    deeplink {
        path("/serie_static/..*")
    }
}
