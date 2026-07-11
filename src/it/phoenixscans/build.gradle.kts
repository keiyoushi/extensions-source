import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Phoenix Scans"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "pizzareader"

    source {
        lang = "it"
        baseUrl = "https://www.phoenixscans.com"
        versionId = 2
    }
}
