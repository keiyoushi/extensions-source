import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Soft Epsilon Scan"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "pam"

    source {
        lang = "fr"
        baseUrl = "https://epsilonsoft.to"
    }
}
