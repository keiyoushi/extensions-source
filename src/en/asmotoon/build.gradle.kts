import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Asmodeus Scans"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "keyoapp"

    source {
        lang = "en"
        baseUrl = "https://asmotoon.com"
    }
}
