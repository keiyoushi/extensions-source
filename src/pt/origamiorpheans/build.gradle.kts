import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Origami Orpheans"
    versionCode = 11
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "pt-BR"
        baseUrl = "https://origami-orpheans.com"
        versionId = 2
    }
}
