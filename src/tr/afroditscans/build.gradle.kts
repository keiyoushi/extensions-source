import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Afrodit Scans"
    versionCode = 31
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "uzaymanga"

    source {
        lang = "tr"
        baseUrl = "https://afroditscans.com"
        versionId = 2
    }
}
