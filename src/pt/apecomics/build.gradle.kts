import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Capitoons"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangawork"

    source {
        lang = "pt-BR"
        baseUrl = "https://capitoons.com"
        id = 4475020039832513819L
    }
}
