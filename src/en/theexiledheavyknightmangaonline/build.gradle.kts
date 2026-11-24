import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read The Exiled Heavy Knight Manga Online"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "solomanga"

    source {
        lang = "en"
        baseUrl = "https://w4.theexiledheavyknight.com"
    }
}
