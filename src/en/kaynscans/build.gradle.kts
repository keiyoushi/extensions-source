import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kayn Scans"
    versionCode = 31
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "vinetheme"

    source {
        baseUrl = "https://kaynscans.com"
        lang = "en"
        versionId = 2
    }
}
