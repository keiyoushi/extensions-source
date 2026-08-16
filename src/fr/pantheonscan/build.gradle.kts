import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pantheon Scan"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "fr"
        baseUrl = "https://pantheon-scan.com"
    }
}
