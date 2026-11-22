import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kaiji Manga Online"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "solomanga"

    source {
        lang = "en"
        baseUrl = "https://w9.kaijimanga.com"
    }
}
