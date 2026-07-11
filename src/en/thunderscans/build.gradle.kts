import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Thunder Scans"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://en-thunderscans.com"
    }
}
