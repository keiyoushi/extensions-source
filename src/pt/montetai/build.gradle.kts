import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Monte Tai"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madaralegacy"

    source {
        lang = "pt-BR"
        baseUrl = "https://montetaiscanlator.xyz"
    }
}
