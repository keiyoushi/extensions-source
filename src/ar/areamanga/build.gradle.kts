import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Area Manga"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        name = "أريا مانجا"
        lang = "ar"
        baseUrl = "https://ar.kenmanga.com"
    }
}
