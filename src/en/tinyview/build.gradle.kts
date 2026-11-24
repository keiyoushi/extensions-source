import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tinyview"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://tinyview.com"
    }

    deeplink {
        host("tinyview.com")
        host("www.tinyview.com")
        path("/..*")
    }
}
