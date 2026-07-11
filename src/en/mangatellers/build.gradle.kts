import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangatellers"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "foolslide"

    source {
        lang = "en"
        baseUrl = "https://reader.mangatellers.gr"
    }
}
