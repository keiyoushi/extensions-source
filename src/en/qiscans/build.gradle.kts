import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Qi Scans"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "ezmanhwa"

    source {
        name = "QiScans"
        lang = "en"
        baseUrl = "https://qimanga.com"
    }
}
