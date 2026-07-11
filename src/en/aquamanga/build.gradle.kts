import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Aqua Manga"
    versionCode = 13
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://aquareader.org"
    }
}
