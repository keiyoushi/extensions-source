import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Duck Scans"
    theme = "duckscans"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://duckscans.com"
    }

    deeplink {
        host("duckscans.com")
        host("www.duckscans.com")
        path("/seri/..*")
    }
}
