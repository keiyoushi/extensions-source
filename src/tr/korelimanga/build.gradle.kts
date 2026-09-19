import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Koreli Manga"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "initmanga"

    source {
        lang = "tr"
        baseUrl = "https://korelimanga.com"
    }
}
