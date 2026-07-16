import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Azora"
    versionCode = 45
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "iken"

    source {
        baseUrl = "https://azorafly.com"
        lang = "ar"
        versionId = 2
    }
}
