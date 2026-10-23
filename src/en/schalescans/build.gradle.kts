import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Schale Scans"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://www.schalescans.com"
    }

    deeplink {
        host("www.schalescans.com")
        host("r18.schalescans.com")
        path("/manga/..*")
        path("/read/..*")
    }
}
