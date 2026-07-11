import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa List"
    versionCode = 7
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "id"
        baseUrl = "https://manhwalist02.asia"
    }
}
