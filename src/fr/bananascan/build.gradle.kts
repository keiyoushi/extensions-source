import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Harmony-Scan"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "fr"
        baseUrl = "https://harmony-scan.fr"
        id = 3121632933690925888L
    }
}
