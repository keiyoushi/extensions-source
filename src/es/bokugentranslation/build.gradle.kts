import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BokugenTranslation"
    versionCode = 18
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "es"
        baseUrl = "https://bokugents.com"
    }
}
