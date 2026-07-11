import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Spicy Scan"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "spicytheme"

    source {
        lang = "es"
        baseUrl = "https://spicyseries.com"
    }
}
