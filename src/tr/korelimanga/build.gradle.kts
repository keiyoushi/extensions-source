import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Koreli Manga"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "initmanga"

    source {
        lang = "tr"
        baseUrl = "https://korelimanga.com"
    }
}
