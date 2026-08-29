import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Diva Scans"
    versionCode = 25
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "vinetheme"

    source {
        lang = "en"
        baseUrl = "https://divascans.org"
        versionId = 2
    }
}
