import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Fleur Blanche"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://fbsquadx.com"
    }
}
