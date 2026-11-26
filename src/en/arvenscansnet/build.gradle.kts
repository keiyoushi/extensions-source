import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Arven Scans"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://arventoons.com"
        versionId = 2
    }

    deeplink {
        host("arventoons.com")
        host("www.arventoons.com")
        path("/series/..*")
        path("/reader/..*")
    }
}
