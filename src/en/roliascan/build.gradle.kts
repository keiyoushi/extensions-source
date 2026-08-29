import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rolia Scan"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangataro"

    source {
        lang = "en"
        baseUrl = "https://roliascan.com"
    }
}
