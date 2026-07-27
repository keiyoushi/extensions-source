import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Inmortal Scan"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://scan-inmortal.com"
    }
}
