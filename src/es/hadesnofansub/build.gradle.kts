import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hades no Fansub"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://lectorhades.latamtoon.com"
    }
}
