import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kayn Scans"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "iken"

    source {
        baseUrl = "https://kaynscan.org"
        lang = "en"
    }
}
