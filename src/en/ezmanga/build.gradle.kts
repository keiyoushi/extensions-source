import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "EZmanga"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "ezmanhwa"

    source {
        lang = "en"
        baseUrl = "https://ezmanga.org"
        versionId = 5
    }
}
