import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Fleur Blanche"
    versionCode = 5
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "pt-BR"
        baseUrl = "https://fbsquadx.com"
    }
}
