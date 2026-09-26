import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Epsilon Scan"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "pam"

    source {
        lang = "fr"
        baseUrl = "https://epsilonscan.to"
        versionId = 2
    }
}
