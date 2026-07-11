import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Capitoons"
    versionCode = 44
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangawork"

    source {
        lang = "pt-BR"
        baseUrl = "https://capitoons.com"
        id = 4475020039832513819L
    }
}
