import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BigSolo"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://bigsolo.org"
        id = 4410528266393104437L
    }

    deeplink {
        host("bigsolo.org")
        host("www.bigsolo.org")
        path("/..*")
    }
}
