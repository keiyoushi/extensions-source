import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Spicy Scan"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "spicytheme"

    source {
        lang = "es"
        baseUrl = "https://spicyseries.com"
    }
}
