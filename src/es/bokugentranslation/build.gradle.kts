import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BokugenTranslation"
    versionCode = 19
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "es"
        baseUrl = "https://bokugents.com"
    }
}
