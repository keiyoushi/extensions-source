import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaGet"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://manhwaget.com"
    }
}
