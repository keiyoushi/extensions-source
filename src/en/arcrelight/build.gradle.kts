import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Arc-Relight"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangadventure"

    source {
        lang = "en"
        baseUrl = "https://arc-relight.com"
        id = 6809555026902049727L
    }
}
